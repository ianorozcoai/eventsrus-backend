package com.backend.eventsrus.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business numbers for the admin Analytics page (protected by SecurityConfig's
 * /api/v1/admin/** -> hasRole("ADMIN") rule). Read-only SQL over existing tables;
 * fake and deleted accounts are always excluded so the numbers are real people.
 * All dates are Philippine calendar days (Asia/Manila).
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private static final String MNL = "Asia/Manila";
    /** A "real" account: not fake, not deleted. */
    private static final String REAL = "u.fake_account = false AND u.deleted_at IS NULL";
    /** Supplier = vendor role, or someone who signed up with supplier intent but has not finished. */
    private static final String IS_SUPPLIER = "(u.role = 'VENDOR' OR u.signup_intent = 'VENDOR')";

    private final JdbcTemplate jdbc;

    public record DayCount(String date, long planners, long suppliers) {}
    public record Labelled(String label, long count) {}
    public record Totals(
            long planners, long suppliers, long suppliersWithListing, long suppliersVerified,
            long proSuppliers, long active7d, long active30d,
            long onFreeAccessOffer, long reachedFirstBookings) {}
    public record Funnel(
            long signups, long suppliers, long suppliersWithListing,
            long planners, long plannersWithEvent, long plannersWhoRequestedQuote,
            long events, long leads, long quotations, long bookings) {}
    public record AnalyticsResponse(
            int days, Totals totals, Funnel funnel, List<DayCount> signupsByDay,
            List<Labelled> signupsBySource, List<Labelled> suppliersByCity,
            List<Labelled> suppliersByBusinessType) {}

    @GetMapping
    public AnalyticsResponse get(@RequestParam(defaultValue = "30") int days) {
        int d = Math.max(1, Math.min(days, 365));
        return new AnalyticsResponse(d, totals(), funnel(d), signupsByDay(d), signupsBySource(d),
                suppliersByCity(), suppliersByBusinessType());
    }

    private long one(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private Totals totals() {
        long planners = one("SELECT count(*) FROM users u WHERE " + REAL
                + " AND u.role = 'PLANNER' AND coalesce(u.signup_intent,'') <> 'VENDOR'");
        long suppliers = one("SELECT count(*) FROM users u WHERE " + REAL + " AND u.role = 'VENDOR'");
        long withListing = one("SELECT count(DISTINCT vp.id) FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id"
                + " JOIN vendor_packages p ON p.vendor_profile_id = vp.id AND p.active = true WHERE " + REAL);
        long verified = one("SELECT count(*) FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id WHERE "
                + REAL + " AND vp.verified = true");
        long pro = one("SELECT count(DISTINCT s.user_id) FROM vendor_subscriptions s JOIN users u ON u.id = s.user_id"
                + " WHERE " + REAL + " AND s.status = 'ACTIVE'");
        long a7 = one("SELECT count(*) FROM users u WHERE " + REAL + " AND u.last_login_at >= now() - interval '7 days'");
        long a30 = one("SELECT count(*) FROM users u WHERE " + REAL + " AND u.last_login_at >= now() - interval '30 days'");
        // Suppliers whose latest subscription is a free grant that runs "until N counted bookings", and how many of
        // them already have N counted bookings (a booking counts once the planner submitted payment).
        String latestOffer = "FROM vendor_subscriptions s JOIN users u ON u.id = s.user_id WHERE " + REAL
                + " AND s.bookings_goal IS NOT NULL AND s.billing_source = 'FREE_GRANT'"
                + " AND NOT EXISTS (SELECT 1 FROM vendor_subscriptions s2 WHERE s2.user_id = s.user_id"
                + " AND s2.current_period_start > s.current_period_start)";
        long onOffer = one("SELECT count(*) " + latestOffer);
        long reached = one("SELECT count(*) " + latestOffer
                + " AND (SELECT count(DISTINCT b.id) FROM bookings b JOIN booking_status_history h ON h.booking_id = b.id"
                + " WHERE b.vendor_user_id = s.user_id AND h.to_status IN ('PAYMENT_SUBMITTED', 'BOOKED')"
                + " AND b.status NOT IN ('PAYMENT_REJECTED', 'DECLINED')) >= s.bookings_goal");
        return new Totals(planners, suppliers, withListing, verified, pro, a7, a30, onOffer, reached);
    }

    private Funnel funnel(int days) {
        String since = " >= (now() - make_interval(days => ?))";
        long signups = one("SELECT count(*) FROM users u WHERE " + REAL + " AND u.created_at" + since, days);
        long suppliers = one("SELECT count(*) FROM users u WHERE " + REAL + " AND " + IS_SUPPLIER
                + " AND u.created_at" + since, days);
        long withListing = one("SELECT count(DISTINCT vp.id) FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id"
                + " JOIN vendor_packages p ON p.vendor_profile_id = vp.id AND p.active = true WHERE " + REAL
                + " AND u.created_at" + since, days);
        long planners = signups - suppliers;
        long plannersWithEvent = one("SELECT count(DISTINCT e.planner_id) FROM events e JOIN users u ON u.id = e.planner_id"
                + " WHERE " + REAL + " AND u.created_at" + since, days);
        long plannersQuote = one("SELECT count(DISTINCT q.planner_user_id) FROM quotations q JOIN users u ON u.id = q.planner_user_id"
                + " WHERE " + REAL + " AND u.created_at" + since, days);
        long events = one("SELECT count(*) FROM events e JOIN users u ON u.id = e.planner_id WHERE " + REAL
                + " AND e.created_at" + since, days);
        long leads = one("SELECT count(*) FROM leads l JOIN users u ON u.id = l.planner_user_id WHERE " + REAL
                + " AND l.created_at" + since, days);
        long quotes = one("SELECT count(*) FROM quotations q JOIN users u ON u.id = q.planner_user_id WHERE " + REAL
                + " AND q.created_at" + since, days);
        long bookings = one("SELECT count(*) FROM bookings b JOIN users u ON u.id = b.planner_user_id WHERE " + REAL
                + " AND b.created_at" + since, days);
        return new Funnel(signups, suppliers, withListing, Math.max(planners, 0), plannersWithEvent,
                plannersQuote, events, leads, quotes, bookings);
    }

    private List<DayCount> signupsByDay(int days) {
        String sql = "SELECT to_char(g.d::date, 'YYYY-MM-DD') AS day,"
                + " count(u.id) FILTER (WHERE NOT " + IS_SUPPLIER + ") AS planners,"
                + " count(u.id) FILTER (WHERE " + IS_SUPPLIER + ") AS suppliers"
                + " FROM generate_series((now() AT TIME ZONE '" + MNL + "')::date - (? - 1), (now() AT TIME ZONE '" + MNL + "')::date, interval '1 day') AS g(d)"
                + " LEFT JOIN users u ON (u.created_at AT TIME ZONE '" + MNL + "')::date = g.d::date AND " + REAL
                + " GROUP BY g.d ORDER BY g.d";
        return jdbc.query(sql, (rs, i) -> new DayCount(rs.getString("day"), rs.getLong("planners"), rs.getLong("suppliers")), days);
    }

    private List<Labelled> signupsBySource(int days) {
        return jdbc.query("SELECT coalesce(u.signup_source, 'UNKNOWN') AS label, count(*) AS c FROM users u WHERE " + REAL
                + " AND u.created_at >= (now() - make_interval(days => ?)) GROUP BY 1 ORDER BY c DESC",
                (rs, i) -> new Labelled(rs.getString("label"), rs.getLong("c")), days);
    }

    private List<Labelled> suppliersByCity() {
        return jdbc.query("SELECT initcap(trim(vp.city)) AS label, count(*) AS c FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id"
                + " WHERE " + REAL + " AND coalesce(trim(vp.city),'') <> '' GROUP BY 1 ORDER BY c DESC LIMIT 12",
                (rs, i) -> new Labelled(rs.getString("label"), rs.getLong("c")));
    }

    private List<Labelled> suppliersByBusinessType() {
        return jdbc.query("SELECT coalesce(vp.business_type, 'UNSET') AS label, count(*) AS c FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id"
                + " WHERE " + REAL + " GROUP BY 1 ORDER BY c DESC",
                (rs, i) -> new Labelled(rs.getString("label"), rs.getLong("c")));
    }
}
