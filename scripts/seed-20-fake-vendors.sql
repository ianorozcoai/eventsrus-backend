-- =============================================================================
--  seed-20-fake-vendors.sql
-- =============================================================================
--  Creates 20 fully-formed, curated (not randomized) vendors for EventsRUs,
--  flagged fake_account = true so they can be identified/removed later once
--  real vendors are onboarded. Metro Manila + nearby provinces focus only
--  (no "Entire Philippines" catch-all, unlike scripts/seed-500-vendors.sql).
--
--  Every vendor gets:
--    * a users row (role VENDOR, fake_account = true, owner identity is
--      Ian/Orozco/09075116813 - all inquiries route to the admin, not a
--      real per-vendor person)
--    * a vendor_profiles row (contact_email = admin.eventsrus@gmail.com so
--      any storefront inquiry lands in the admin inbox; logo/QR/legal-doc
--      fields left NULL - images uploaded manually later)
--    * an ACTIVE PRO subscription valid 10 years (VendorPlanService's gate
--      for showing up in planner search)
--    * operating areas: its own city's region + 2-3 nearby provinces
--    * 4 packages, ALL pricing_type = QUOTE (no price/min/max ever set -
--      standing instruction, see memory fake-vendor-seed-quote-pricing)
--    * verified left at its column default (false) - not explicitly set
--    * base_price left NULL - standing instruction, always Request for Quote
--
--  Tagged by google_id LIKE 'fakev-%' / users.fake_account = true so a
--  future cleanup script can remove it without touching real data.
--
--  USAGE:
--    PGPASSWORD=postgres psql -h localhost -U postgres -d eventsrus \
--        -f scripts/seed-20-fake-vendors.sql
-- =============================================================================

DO $DO$
DECLARE
    v_config jsonb := $J$
    [
      {"seq":1,"type":"CATERING","ptype":"SERVICE","bn":"Manila Fiesta Catering","city":"Quezon City","prov":"Metro Manila (NCR)",
       "desc":"Manila Fiesta Catering is a full-service catering provider based in Quezon City, Metro Manila. We handle weddings, debuts, corporate events, and private celebrations across the metro, known for generous Filipino-fusion menus and reliable on-time service.",
       "overview":"With years of experience serving Metro Manila celebrations, Manila Fiesta Catering has built a reputation for warm hospitality, flexible menu customization, and a service team that treats every event like their own family's.",
       "nearby":["Cavite","Rizal","Bulacan"],
       "events":["WEDDING","BIRTHDAY","ANNIVERSARY","CORPORATE_EVENT","DEBUT"],
       "cap":[50,500],"lead":14,
       "packages":[
         {"n":"Buffet Set A - 50 pax","d":"Three mains, rice, pasta, dessert, and bottomless iced tea. Includes skirted buffet styling, chafing dishes, uniformed servers, and full setup and cleanup."},
         {"n":"Buffet Set B - 100 pax","d":"Four mains, two sides, pasta, garden salad, a dessert station, and drinks. Includes a menu tasting for two, themed buffet styling, and full service staff."},
         {"n":"Plated Fine-Dining - 150 pax","d":"Five-course plated dinner with a pre-event tasting, china, glassware and linen, banquet captains, and a mobile kitchen brigade."},
         {"n":"Grazing Table and Cocktail Catering","d":"Grazing tables, passed canapes, and a beverage bar for cocktail-style receptions. Final menu, stations, and headcount quoted per event."}
       ]},

      {"seq":2,"type":"CATERING","ptype":"SERVICE","bn":"Kusina ni Aling Rosa Catering","city":"Makati","prov":"Metro Manila (NCR)",
       "desc":"Kusina ni Aling Rosa Catering is a home-style Filipino catering business based in Makati, Metro Manila, specializing in weddings, birthdays, and corporate gatherings with recipes passed down through generations.",
       "overview":"What started as a small family kitchen has grown into a trusted name for authentic, comforting Filipino cuisine at celebrations across the metro - every dish made from scratch, never pre-packaged.",
       "nearby":["Rizal","Laguna","Cavite"],
       "events":["WEDDING","BIRTHDAY","ANNIVERSARY","REUNION","CORPORATE_EVENT"],
       "cap":[50,400],"lead":10,
       "packages":[
         {"n":"Buffet Set A - 50 pax","d":"Three mains, rice, pasta, dessert, and bottomless iced tea. Includes skirted buffet styling, chafing dishes, uniformed servers, and full setup and cleanup."},
         {"n":"Buffet Set B - 100 pax","d":"Four mains, two sides, pasta, garden salad, a dessert station, and drinks. Includes a menu tasting for two, themed buffet styling, and full service staff."},
         {"n":"Plated Fine-Dining - 150 pax","d":"Five-course plated dinner with a pre-event tasting, china, glassware and linen, banquet captains, and a mobile kitchen brigade."},
         {"n":"Grazing Table and Cocktail Catering","d":"Grazing tables, passed canapes, and a beverage bar for cocktail-style receptions. Final menu, stations, and headcount quoted per event."}
       ]},

      {"seq":3,"type":"CATERING","ptype":"SERVICE","bn":"Golden Spoon Catering Services","city":"Pasig","prov":"Metro Manila (NCR)",
       "desc":"Golden Spoon Catering Services is a Pasig-based catering company handling weddings, corporate functions, and private parties throughout Metro Manila and nearby provinces, known for polished presentation and consistent quality.",
       "overview":"Golden Spoon has catered hundreds of celebrations across the metro, with a team known for meticulous plating, punctual service, and menus that balance Filipino favorites with modern presentation.",
       "nearby":["Rizal","Cavite","Bulacan"],
       "events":["WEDDING","CORPORATE_EVENT","BIRTHDAY","GALA","PRODUCT_LAUNCH"],
       "cap":[80,500],"lead":14,
       "packages":[
         {"n":"Buffet Set A - 50 pax","d":"Three mains, rice, pasta, dessert, and bottomless iced tea. Includes skirted buffet styling, chafing dishes, uniformed servers, and full setup and cleanup."},
         {"n":"Buffet Set B - 100 pax","d":"Four mains, two sides, pasta, garden salad, a dessert station, and drinks. Includes a menu tasting for two, themed buffet styling, and full service staff."},
         {"n":"Plated Fine-Dining - 150 pax","d":"Five-course plated dinner with a pre-event tasting, china, glassware and linen, banquet captains, and a mobile kitchen brigade."},
         {"n":"Grazing Table and Cocktail Catering","d":"Grazing tables, passed canapes, and a beverage bar for cocktail-style receptions. Final menu, stations, and headcount quoted per event."}
       ]},

      {"seq":4,"type":"PHOTO_AND_VIDEO","ptype":"SERVICE","bn":"Frame & Film Studios","city":"Mandaluyong","prov":"Metro Manila (NCR)",
       "desc":"Frame & Film Studios is a Mandaluyong-based photography and videography team covering weddings, debuts, and corporate events across Metro Manila, blending documentary-style storytelling with cinematic visuals.",
       "overview":"Frame & Film Studios' small, tight-knit crew has covered hundreds of celebrations, known for candid, emotion-driven coverage and same-day-edit films that guests still talk about.",
       "nearby":["Rizal","Cavite"],
       "events":["WEDDING","DEBUT","ENGAGEMENT_PARTY","CORPORATE_EVENT"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Prenup / Engagement Session","d":"Four-hour shoot at one location, one photographer and one videographer, up to three outfit changes, 60+ retouched images, and a 60-second highlight reel."},
         {"n":"Half-Day Event Coverage","d":"Six hours of coverage, two photographers and one cinematographer, all edited high-res photos in an online gallery, and a three-minute same-day-edit film."},
         {"n":"Full-Day Wedding Coverage","d":"Twelve hours of coverage, lead and second shooter, drone, same-day edit, 400+ edited photos, a cinematic feature film, and a printed lay-flat album."},
         {"n":"Add-On: Photo Booth and Extra Hours","d":"On-site photo booth with unlimited prints plus additional coverage hours and raw-file turnover. Priced to your final timeline."}
       ]},

      {"seq":5,"type":"PHOTO_AND_VIDEO","ptype":"SERVICE","bn":"Manila Lens Photography","city":"Taguig","prov":"Metro Manila (NCR)",
       "desc":"Manila Lens Photography is a Taguig-based studio specializing in wedding, debut, and corporate event photography and videography, serving clients across Metro Manila and nearby provinces.",
       "overview":"Manila Lens Photography's team is known for a clean, editorial style and fast turnaround - full galleries delivered within weeks, not months, without sacrificing quality.",
       "nearby":["Rizal","Laguna","Cavite"],
       "events":["WEDDING","DEBUT","CORPORATE_EVENT","BIRTHDAY"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Prenup / Engagement Session","d":"Four-hour shoot at one location, one photographer and one videographer, up to three outfit changes, 60+ retouched images, and a 60-second highlight reel."},
         {"n":"Half-Day Event Coverage","d":"Six hours of coverage, two photographers and one cinematographer, all edited high-res photos in an online gallery, and a three-minute same-day-edit film."},
         {"n":"Full-Day Wedding Coverage","d":"Twelve hours of coverage, lead and second shooter, drone, same-day edit, 400+ edited photos, a cinematic feature film, and a printed lay-flat album."},
         {"n":"Add-On: Photo Booth and Extra Hours","d":"On-site photo booth with unlimited prints plus additional coverage hours and raw-file turnover. Priced to your final timeline."}
       ]},

      {"seq":6,"type":"PHOTO_AND_VIDEO","ptype":"SERVICE","bn":"Cinematic Moments PH","city":"Quezon City","prov":"Metro Manila (NCR)",
       "desc":"Cinematic Moments PH is a Quezon City-based wedding and events photo/video team known for cinematic storytelling, serving Metro Manila and nearby provinces.",
       "overview":"Cinematic Moments PH treats every event like a short film in the making, pairing drone cinematography with intimate handheld coverage to capture both the grand moments and the quiet ones.",
       "nearby":["Bulacan","Rizal","Cavite"],
       "events":["WEDDING","DEBUT","ENGAGEMENT_PARTY","ANNIVERSARY"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Prenup / Engagement Session","d":"Four-hour shoot at one location, one photographer and one videographer, up to three outfit changes, 60+ retouched images, and a 60-second highlight reel."},
         {"n":"Half-Day Event Coverage","d":"Six hours of coverage, two photographers and one cinematographer, all edited high-res photos in an online gallery, and a three-minute same-day-edit film."},
         {"n":"Full-Day Wedding Coverage","d":"Twelve hours of coverage, lead and second shooter, drone, same-day edit, 400+ edited photos, a cinematic feature film, and a printed lay-flat album."},
         {"n":"Add-On: Photo Booth and Extra Hours","d":"On-site photo booth with unlimited prints plus additional coverage hours and raw-file turnover. Priced to your final timeline."}
       ]},

      {"seq":7,"type":"FLORAL_SERVICES","ptype":"SERVICE","bn":"Bloom & Blossom Florals","city":"San Juan","prov":"Metro Manila (NCR)",
       "desc":"Bloom & Blossom Florals is a San Juan-based floral design studio serving weddings, debuts, and corporate events across Metro Manila, known for lush, garden-inspired arrangements.",
       "overview":"Bloom & Blossom Florals sources fresh blooms daily and designs every arrangement in-house, from a single bridal bouquet to a full ceremony-to-reception floral program.",
       "nearby":["Rizal","Cavite"],
       "events":["WEDDING","DEBUT","ANNIVERSARY","ENGAGEMENT_PARTY"],
       "cap":null,"lead":14,
       "packages":[
         {"n":"Bridal Flowers Set","d":"A bridal bouquet, three entourage bouquets, four boutonnieres, two corsages, and delivery on the day."},
         {"n":"Ceremony + Reception Florals","d":"Aisle arrangements, altar or arch florals, a couple's-table runner, and centerpieces for up to 12 guest tables."},
         {"n":"Full Floral Design","d":"Complete floral styling with statement installations, a ceiling or arch build, lounge florals, and a setup and teardown team."},
         {"n":"Corporate / Event Florals","d":"Stage, lobby, and table florals for launches and galas. Palette and volume quoted per event."}
       ]},

      {"seq":8,"type":"FLORAL_SERVICES","ptype":"SERVICE","bn":"Petals of Manila","city":"Marikina","prov":"Metro Manila (NCR)",
       "desc":"Petals of Manila is a Marikina-based floral studio offering bridal and event florals for weddings, debuts, and celebrations across Metro Manila and nearby provinces.",
       "overview":"Petals of Manila's florists work closely with couples to translate a mood board into real, living arrangements - from minimalist and modern to full garden-romantic installations.",
       "nearby":["Rizal","Bulacan"],
       "events":["WEDDING","DEBUT","BRIDAL_SHOWER","ANNIVERSARY"],
       "cap":null,"lead":14,
       "packages":[
         {"n":"Bridal Flowers Set","d":"A bridal bouquet, three entourage bouquets, four boutonnieres, two corsages, and delivery on the day."},
         {"n":"Ceremony + Reception Florals","d":"Aisle arrangements, altar or arch florals, a couple's-table runner, and centerpieces for up to 12 guest tables."},
         {"n":"Full Floral Design","d":"Complete floral styling with statement installations, a ceiling or arch build, lounge florals, and a setup and teardown team."},
         {"n":"Corporate / Event Florals","d":"Stage, lobby, and table florals for launches and galas. Palette and volume quoted per event."}
       ]},

      {"seq":9,"type":"CAKE_AND_PASTRIES","ptype":"PRODUCT","bn":"Sweet Layers Cake Studio","city":"Pasig","prov":"Metro Manila (NCR)",
       "desc":"Sweet Layers Cake Studio is a Pasig-based bakeshop crafting custom celebration and wedding cakes, plus dessert tables, for events across Metro Manila.",
       "overview":"Every cake at Sweet Layers Cake Studio is baked to order and hand-decorated, with a menu built around client tastings and a design consultation before anything goes in the oven.",
       "nearby":["Rizal","Cavite","Laguna"],
       "events":["WEDDING","BIRTHDAY","DEBUT","ANNIVERSARY"],
       "cap":[40,250],"lead":10,
       "packages":[
         {"n":"Celebration Cake - 2 tiers","d":"A two-tier buttercream or fondant cake serving up to 60, one custom design, one flavor, and a cake tasting for two."},
         {"n":"Wedding Cake - 3 tiers + faux","d":"Three real tiers plus a faux display serving up to 120, a custom design, two flavors, delivery, and setup."},
         {"n":"Dessert Table - 5 varieties","d":"A styled dessert table with five treats (cupcakes, verrines, cookies, macarons, mini tarts) for up to 150, plus stands and signage."},
         {"n":"Grand Dessert Spread / Corporate Orders","d":"Large dessert bars and branded pastry boxes for corporate events. Assortment and volume quoted per order."}
       ]},

      {"seq":10,"type":"CAKE_AND_PASTRIES","ptype":"PRODUCT","bn":"Manila Buttercream Bakeshop","city":"Quezon City","prov":"Metro Manila (NCR)",
       "desc":"Manila Buttercream Bakeshop is a Quezon City bakeshop specializing in custom wedding and celebration cakes and dessert tables, serving clients across Metro Manila and nearby provinces.",
       "overview":"Manila Buttercream Bakeshop built its name on classic Filipino flavors reimagined in modern cake designs, with a small team that hand-pipes every detail.",
       "nearby":["Bulacan","Rizal"],
       "events":["WEDDING","BIRTHDAY","DEBUT","GRADUATION"],
       "cap":[40,250],"lead":10,
       "packages":[
         {"n":"Celebration Cake - 2 tiers","d":"A two-tier buttercream or fondant cake serving up to 60, one custom design, one flavor, and a cake tasting for two."},
         {"n":"Wedding Cake - 3 tiers + faux","d":"Three real tiers plus a faux display serving up to 120, a custom design, two flavors, delivery, and setup."},
         {"n":"Dessert Table - 5 varieties","d":"A styled dessert table with five treats (cupcakes, verrines, cookies, macarons, mini tarts) for up to 150, plus stands and signage."},
         {"n":"Grand Dessert Spread / Corporate Orders","d":"Large dessert bars and branded pastry boxes for corporate events. Assortment and volume quoted per order."}
       ]},

      {"seq":11,"type":"HAIR_AND_MAKEUP","ptype":"SERVICE","bn":"Glow Up Artistry Studio","city":"Makati","prov":"Metro Manila (NCR)",
       "desc":"Glow Up Artistry Studio is a Makati-based hair and makeup team serving brides, debutantes, and event guests across Metro Manila.",
       "overview":"Glow Up Artistry Studio's artists specialize in long-lasting, photo-ready looks, with both studio and on-location service and a standby touch-up kit for every booking.",
       "nearby":["Rizal","Cavite"],
       "events":["WEDDING","DEBUT","BRIDAL_SHOWER","ENGAGEMENT_PARTY"],
       "cap":null,"lead":10,
       "packages":[
         {"n":"Party Glam - 1 face","d":"One full hair and makeup application for an event guest or debutante, false lashes included, at the studio or on location."},
         {"n":"Bridal Trial + Wedding Day","d":"One pre-wedding trial plus wedding-day hair and makeup for the bride, an airbrush option, lashes, and a touch-up kit."},
         {"n":"Bride + Entourage of 5","d":"The bride plus five glam sessions, one artist and one hair stylist, on-location service, and a standby touch-up artist."},
         {"n":"Large Entourage / Editorial","d":"Big entourages, production shoots, and multi-day coverage. Team size and call times quoted per booking."}
       ]},

      {"seq":12,"type":"HAIR_AND_MAKEUP","ptype":"SERVICE","bn":"Radiance HMUA Collective","city":"Taguig","prov":"Metro Manila (NCR)",
       "desc":"Radiance HMUA Collective is a Taguig-based collective of hair and makeup artists covering weddings, debuts, and events throughout Metro Manila and nearby provinces.",
       "overview":"Radiance HMUA Collective pairs each client with an artist suited to their preferred look, from soft natural glam to full editorial styling, with every kit sanitized between bookings.",
       "nearby":["Rizal","Laguna","Cavite"],
       "events":["WEDDING","DEBUT","BIRTHDAY","BACHELORETTE_PARTY"],
       "cap":null,"lead":10,
       "packages":[
         {"n":"Party Glam - 1 face","d":"One full hair and makeup application for an event guest or debutante, false lashes included, at the studio or on location."},
         {"n":"Bridal Trial + Wedding Day","d":"One pre-wedding trial plus wedding-day hair and makeup for the bride, an airbrush option, lashes, and a touch-up kit."},
         {"n":"Bride + Entourage of 5","d":"The bride plus five glam sessions, one artist and one hair stylist, on-location service, and a standby touch-up artist."},
         {"n":"Large Entourage / Editorial","d":"Big entourages, production shoots, and multi-day coverage. Team size and call times quoted per booking."}
       ]},

      {"seq":13,"type":"HAIR_AND_MAKEUP","ptype":"SERVICE","bn":"Bella Faces Makeup Studio","city":"Mandaluyong","prov":"Metro Manila (NCR)",
       "desc":"Bella Faces Makeup Studio is a Mandaluyong-based bridal and event makeup studio serving Metro Manila and nearby provinces, known for a soft, natural signature look.",
       "overview":"Bella Faces Makeup Studio built its following on airbrush bridal makeup that lasts through a full day of photos, tears, and dancing - without needing a single touch-up.",
       "nearby":["Rizal","Bulacan"],
       "events":["WEDDING","DEBUT","GRADUATION","BIRTHDAY"],
       "cap":null,"lead":10,
       "packages":[
         {"n":"Party Glam - 1 face","d":"One full hair and makeup application for an event guest or debutante, false lashes included, at the studio or on location."},
         {"n":"Bridal Trial + Wedding Day","d":"One pre-wedding trial plus wedding-day hair and makeup for the bride, an airbrush option, lashes, and a touch-up kit."},
         {"n":"Bride + Entourage of 5","d":"The bride plus five glam sessions, one artist and one hair stylist, on-location service, and a standby touch-up artist."},
         {"n":"Large Entourage / Editorial","d":"Big entourages, production shoots, and multi-day coverage. Team size and call times quoted per booking."}
       ]},

      {"seq":14,"type":"SOUVENIR_GIVEAWAYS","ptype":"PRODUCT","bn":"Treasured Tokens Giveaways","city":"Caloocan","prov":"Metro Manila (NCR)",
       "desc":"Treasured Tokens Giveaways is a Caloocan-based party favor and giveaway supplier serving weddings, birthdays, and corporate events across Metro Manila.",
       "overview":"Treasured Tokens Giveaways designs and packages every favor in-house, from simple scented candles to fully branded corporate tokens, with custom tags and packaging included.",
       "nearby":["Bulacan","Rizal"],
       "events":["WEDDING","BIRTHDAY","CORPORATE_EVENT","DEBUT"],
       "cap":null,"lead":14,
       "packages":[
         {"n":"Standard Favors - 50 pcs","d":"Fifty personalized favors (scented candles, mini succulents, or ref magnets), custom tags, and gift packaging."},
         {"n":"Premium Favors - 100 pcs","d":"One hundred curated favors with a custom box, ribbon, and printed thank-you card, plus a display basket."},
         {"n":"Corporate Tokens - 150 pcs","d":"One hundred fifty branded tokens (tumblers, tote bags, or desk items), one-color logo print, and individual sleeves."},
         {"n":"Bespoke / Bulk Giveaways","d":"Custom-designed souvenirs at volume. Materials, branding, and lead time quoted per order."}
       ]},

      {"seq":15,"type":"SOUVENIR_GIVEAWAYS","ptype":"PRODUCT","bn":"Manila Keepsakes Co.","city":"Las Pinas","prov":"Metro Manila (NCR)",
       "desc":"Manila Keepsakes Co. is a Las Pinas-based souvenir and giveaway supplier serving celebrations across Metro Manila and nearby provinces.",
       "overview":"Manila Keepsakes Co. specializes in personalized, Instagram-ready party favors, working with clients on custom themes, colors, and packaging for every order.",
       "nearby":["Cavite","Laguna"],
       "events":["WEDDING","BIRTHDAY","DEBUT","PARTY"],
       "cap":null,"lead":14,
       "packages":[
         {"n":"Standard Favors - 50 pcs","d":"Fifty personalized favors (scented candles, mini succulents, or ref magnets), custom tags, and gift packaging."},
         {"n":"Premium Favors - 100 pcs","d":"One hundred curated favors with a custom box, ribbon, and printed thank-you card, plus a display basket."},
         {"n":"Corporate Tokens - 150 pcs","d":"One hundred fifty branded tokens (tumblers, tote bags, or desk items), one-color logo print, and individual sleeves."},
         {"n":"Bespoke / Bulk Giveaways","d":"Custom-designed souvenirs at volume. Materials, branding, and lead time quoted per order."}
       ]},

      {"seq":16,"type":"INVITATIONS","ptype":"PRODUCT","bn":"Paper & Ink Invitation Studio","city":"Quezon City","prov":"Metro Manila (NCR)",
       "desc":"Paper & Ink Invitation Studio is a Quezon City-based stationery studio designing digital and printed invitations for weddings and celebrations across Metro Manila.",
       "overview":"Paper & Ink Invitation Studio designs every suite from scratch around each couple's story, offering both fully digital e-invite suites and premium printed sets.",
       "nearby":["Bulacan","Rizal"],
       "events":["WEDDING","DEBUT","ENGAGEMENT_PARTY","ANNIVERSARY"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Digital Invitation Suite","d":"A custom-designed e-invite with a matching RSVP page, a save-the-date graphic, and two rounds of revisions."},
         {"n":"Classic Printed Set - 100 pcs","d":"One hundred printed invitations with envelope, insert card, and belly band; one design concept and two revisions."},
         {"n":"Premium Suite - 100 pcs","d":"One hundred suites with specialty paper, a foil or letterpress accent, a wax seal, a vellum wrap, and assembly."},
         {"n":"Full Stationery Program","d":"Invitations plus on-the-day paper goods (menus, place cards, signage, programs). Quantities and finishes quoted per set."}
       ]},

      {"seq":17,"type":"INVITATIONS","ptype":"PRODUCT","bn":"Elegant Prints Manila","city":"Pasay","prov":"Metro Manila (NCR)",
       "desc":"Elegant Prints Manila is a Pasay-based invitation and stationery studio serving weddings and debuts across Metro Manila and nearby provinces.",
       "overview":"Elegant Prints Manila blends classic letterpress techniques with modern design, producing invitation suites that guests keep as keepsakes long after the event.",
       "nearby":["Cavite","Rizal"],
       "events":["WEDDING","DEBUT","ANNIVERSARY","GRADUATION"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Digital Invitation Suite","d":"A custom-designed e-invite with a matching RSVP page, a save-the-date graphic, and two rounds of revisions."},
         {"n":"Classic Printed Set - 100 pcs","d":"One hundred printed invitations with envelope, insert card, and belly band; one design concept and two revisions."},
         {"n":"Premium Suite - 100 pcs","d":"One hundred suites with specialty paper, a foil or letterpress accent, a wax seal, a vellum wrap, and assembly."},
         {"n":"Full Stationery Program","d":"Invitations plus on-the-day paper goods (menus, place cards, signage, programs). Quantities and finishes quoted per set."}
       ]},

      {"seq":18,"type":"DECORATION_PRODUCTION","ptype":"SERVICE","bn":"Grand Affairs Styling & Production","city":"Bacoor","prov":"Cavite",
       "desc":"Grand Affairs Styling & Production is a Bacoor, Cavite-based event styling and production company serving weddings and corporate events across Metro Manila and nearby provinces.",
       "overview":"Grand Affairs Styling & Production handles everything from ceremony aisle styling to full-room design and build, with an in-house fabrication team for custom backdrops and stage sets.",
       "nearby":["Metro Manila (NCR)","Laguna"],
       "events":["WEDDING","CORPORATE_EVENT","DEBUT","GALA"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Ceremony Styling","d":"Aisle and altar styling, welcome signage, floral or dried arrangements, and setup and teardown."},
         {"n":"Reception Styling - Standard","d":"A stage backdrop, the couple's table, guest centerpieces for up to 15 tables, an entrance arch, and lighting accents."},
         {"n":"Full-Room Design and Build","d":"A custom-fabricated backdrop, ceiling treatment, lounge setup, a full floral program, and a production crew for ingress and egress."},
         {"n":"Themed / Corporate Set Production","d":"Concept-driven set design and fabrication for launches, galas, and shows. Renders and build quoted per concept."}
       ]},

      {"seq":19,"type":"DECORATION_PRODUCTION","ptype":"SERVICE","bn":"Dreamscape Events Production","city":"Antipolo","prov":"Rizal",
       "desc":"Dreamscape Events Production is an Antipolo, Rizal-based styling and production house serving weddings and celebrations across Metro Manila and nearby provinces.",
       "overview":"Dreamscape Events Production is known for immersive, statement-making designs - ceiling installations, custom lounges, and full production builds tailored to each couple's theme.",
       "nearby":["Metro Manila (NCR)","Cavite","Laguna"],
       "events":["WEDDING","DEBUT","ANNIVERSARY","CORPORATE_EVENT"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Ceremony Styling","d":"Aisle and altar styling, welcome signage, floral or dried arrangements, and setup and teardown."},
         {"n":"Reception Styling - Standard","d":"A stage backdrop, the couple's table, guest centerpieces for up to 15 tables, an entrance arch, and lighting accents."},
         {"n":"Full-Room Design and Build","d":"A custom-fabricated backdrop, ceiling treatment, lounge setup, a full floral program, and a production crew for ingress and egress."},
         {"n":"Themed / Corporate Set Production","d":"Concept-driven set design and fabrication for launches, galas, and shows. Renders and build quoted per concept."}
       ]},

      {"seq":20,"type":"LIGHTS_AND_SOUNDS","ptype":"SERVICE","bn":"Prime Audio Visual Solutions","city":"Malolos","prov":"Bulacan",
       "desc":"Prime Audio Visual Solutions is a Malolos, Bulacan-based lights and sound production company serving weddings, corporate events, and concerts across Metro Manila and nearby provinces.",
       "overview":"Prime Audio Visual Solutions runs a full inventory of PA systems, lighting rigs, and LED walls, with an in-house technical crew for events of any scale.",
       "nearby":["Metro Manila (NCR)","Pampanga"],
       "events":["WEDDING","CORPORATE_EVENT","CONCERT","PRODUCT_LAUNCH"],
       "cap":null,"lead":21,
       "packages":[
         {"n":"Basic Party Set","d":"A PA system for up to 100 guests, two wireless mics, four par-can lights, a small mixer, and an operator."},
         {"n":"Reception AV Package","d":"A line-array PA for up to 250 guests, a wireless mic set, moving-head lighting, haze, a projector and screen, and a two-person crew."},
         {"n":"Concert-Grade Production","d":"A full stage PA, monitors, a lighting rig with console, LED par wash, backline, and a technical crew with an engineer."},
         {"n":"LED Wall + Full Production","d":"Large-format audio, lighting, and an LED wall for concerts and corporate shows. System design quoted per venue."}
       ]}
    ]
    $J$;

    v_vendor    jsonb;
    v_pkg       jsonb;
    v_user_id   bigint;
    v_vp_id     bigint;
    v_slug      text;
    v_seq       int;
BEGIN
    FOR v_vendor IN SELECT value FROM jsonb_array_elements(v_config) LOOP
        v_seq  := (v_vendor ->> 'seq')::int;
        v_slug := lower(regexp_replace(v_vendor ->> 'bn', '[^a-zA-Z0-9]+', '-', 'g'));
        v_slug := trim(both '-' from v_slug);

        INSERT INTO users (google_id, email, first_name, last_name, mobile_number, role, signup_intent,
                terms_accepted_at, terms_version, fake_account, created_at, updated_at)
        VALUES ('fakev-' || lpad(v_seq::text, 7, '0'),
                'vendor.' || v_slug || '@eventsrus.app',
                'Ian', 'Orozco', '09075116813', 'VENDOR', 'VENDOR',
                now(), '2026-09-22', true, now(), now())
        RETURNING id INTO v_user_id;

        INSERT INTO vendor_profiles (user_id, owner_name, slug, referral_code, description, contact_email, phone_number,
                city, state, country, business_name, business_type, primary_category,
                max_guest_capacity, lead_time_days, storefront_overview, created_at, updated_at)
        VALUES (v_user_id, 'Ian Orozco', v_slug, 'FKV' || lpad(v_seq::text, 7, '0'),
                v_vendor ->> 'desc', 'admin.eventsrus@gmail.com', '09075116813',
                v_vendor ->> 'city', v_vendor ->> 'prov', 'Philippines',
                v_vendor ->> 'bn', v_vendor ->> 'type', v_vendor ->> 'type',
                CASE WHEN v_vendor -> 'cap' IS NOT NULL AND v_vendor -> 'cap' <> 'null'
                     THEN (v_vendor -> 'cap' ->> 0)::int + floor(random() *
                          (((v_vendor -> 'cap' ->> 1)::int) - ((v_vendor -> 'cap' ->> 0)::int) + 1))::int
                     ELSE NULL END,
                (v_vendor ->> 'lead')::int, v_vendor ->> 'overview', now(), now())
        RETURNING id INTO v_vp_id;

        INSERT INTO vendor_subscriptions (user_id, current_period_start, current_period_end, plan, billing_source, status, created_at, updated_at)
        VALUES (v_user_id, now() - interval '1 day', now() + interval '3650 days', 'PRO', 'FREE_GRANT', 'ACTIVE', now(), now());

        INSERT INTO vendor_operating_areas (vendor_profile_id, area) VALUES (v_vp_id, v_vendor ->> 'prov');
        INSERT INTO vendor_operating_areas (vendor_profile_id, area)
        SELECT v_vp_id, p FROM jsonb_array_elements_text(v_vendor -> 'nearby') AS p
        WHERE p <> (v_vendor ->> 'prov');

        INSERT INTO vendor_catered_event_types (vendor_profile_id, event_type)
        SELECT v_vp_id, e FROM jsonb_array_elements_text(v_vendor -> 'events') AS e;

        FOR v_pkg IN SELECT value FROM jsonb_array_elements(v_vendor -> 'packages') LOOP
            INSERT INTO vendor_packages (vendor_profile_id, name, description, price, package_type, active,
                    pricing_type, min_price, max_price, created_at, updated_at)
            VALUES (v_vp_id, v_pkg ->> 'n', v_pkg ->> 'd', NULL, v_vendor ->> 'ptype', true, 'QUOTE', NULL, NULL, now(), now());
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Seeded % fake vendors, % packages, % subscriptions.',
        (SELECT count(*) FROM users WHERE fake_account = true),
        (SELECT count(*) FROM vendor_packages p JOIN vendor_profiles vp ON vp.id = p.vendor_profile_id
            JOIN users u ON u.id = vp.user_id WHERE u.fake_account = true),
        (SELECT count(*) FROM vendor_subscriptions s JOIN users u ON u.id = s.user_id WHERE u.fake_account = true);
END
$DO$;
