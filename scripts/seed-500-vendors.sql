-- =============================================================================
--  seed-500-vendors.sql   (THROWAWAY TEST DATA - never run against production)
-- =============================================================================
--  Creates a large batch of fully-formed vendors for load-testing how the
--  planner "Recommended Suppliers" / vendor-matching view behaves at scale.
--  No email/Google sign-up involved - a vendor is just rows in users +
--  vendor_profiles + vendor_subscriptions + child tables.
--
--  Each seeded vendor gets:
--    * a users row (role VENDOR, fake unique google_id 'seedv-XXXXXXX')
--    * a vendor_profiles row (realistic name / owner / description / address,
--      unique slug + referral code, lead time, base price)
--    * an ACTIVE PRO subscription valid for 10 years (this is the gate
--      VendorPlanService checks before a vendor is shown in search)
--    * operating area "Entire Philippines" (+ a few random provinces) so every
--      vendor matches any event location
--    * 4-10 random catered event types, biased toward the common ones
--    * exactly 4 packages whose names / copy / prices fit the business type
--    * NO documents, NO payment QR rows, NO package images (left out on purpose)
--    * ~40% verified, ~8% Top Vendor (random)
--
--  Everything is tagged by google_id LIKE 'seedv-%' / email '%@seed.eventsrus.test'
--  so unseed-vendors.sql can remove it without touching real data.
--
--  USAGE (local only):
--    PGPASSWORD=postgres psql -h localhost -U postgres -d eventsrus \
--        -f scripts/seed-500-vendors.sql
--
--  To change the batch size, edit  v_target  below. Business-type counts are
--  weights that sum to 500; at other targets they scale proportionally and the
--  total still lands exactly on v_target (the last type absorbs rounding).
-- =============================================================================

DO $DO$
DECLARE
    v_target   int := 500;          -- <<< how many vendors to create

    v_config   jsonb := $J$
    [
      {"type":"VENUE","weight":80,"ptype":"SERVICE","cap":[80,500],
       "cores":["Events Place","Garden Pavilion","Function Hall","Manor","Estate and Gardens","Grand Ballroom","Rooftop Venue","Events Hall","Riverside Pavilion","Reception Grounds"],
       "packages":[
         {"n":"Intimate Hall Rental","d":"Air-conditioned function room for up to 80 guests, 6-hour venue use, tables and Tiffany chairs, basic mood lighting, parking for 20 cars, and an on-site venue coordinator.","pt":"FIXED","lo":35000,"hi":65000},
         {"n":"Classic Reception Package","d":"Main hall for up to 150 guests, 8-hour use, elegant table and stage setup, air-conditioning, a bridal holding room, house sound system, and a standby generator.","pt":"FIXED","lo":90000,"hi":150000},
         {"n":"Grand Celebration Package","d":"Whole-venue exclusivity for up to 300 guests, 10-hour use, a garden ceremony area, premium lighting and ceiling draping, backup power, security, and a dedicated events team.","pt":"RANGE","lo":200000,"hi":380000},
         {"n":"Custom and Corporate Venue Package","d":"Tailored venue setup for conferences, launches, or multi-day programs. Floor plan, capacity, and inclusions quoted to your requirements.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"CATERING","weight":74,"ptype":"SERVICE","cap":[50,500],
       "cores":["Catering Services","Catering Co.","Kitchen and Catering","Culinary Catering","Fine Dining Catering","Food and Catering","Banquet Catering","Handaan Catering","Salu-Salo Catering","Hapag Catering"],
       "packages":[
         {"n":"Buffet Set A - 50 pax","d":"Three mains, rice, pasta, dessert, and bottomless iced tea. Includes skirted buffet styling, chafing dishes, uniformed servers, and full setup and cleanup.","pt":"FIXED","lo":30000,"hi":48000},
         {"n":"Buffet Set B - 100 pax","d":"Four mains, two sides, pasta, garden salad, a dessert station, and drinks. Includes a menu tasting for two, themed buffet styling, and full service staff.","pt":"FIXED","lo":60000,"hi":98000},
         {"n":"Plated Fine-Dining - 150 pax","d":"Five-course plated dinner with a pre-event tasting, china, glassware and linen, banquet captains, and a mobile kitchen brigade.","pt":"RANGE","lo":130000,"hi":230000},
         {"n":"Grazing Table and Cocktail Catering","d":"Grazing tables, passed canapes, and a beverage bar for cocktail-style receptions. Final menu, stations, and headcount quoted per event.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"PHOTO_AND_VIDEO","weight":68,"ptype":"SERVICE",
       "cores":["Photography","Photo and Video","Films","Studios","Visuals","Photography and Films","Media House","Cinematography","Portraits","Frames and Films"],
       "packages":[
         {"n":"Prenup / Engagement Session","d":"Four-hour shoot at one location, one photographer and one videographer, up to three outfit changes, 60+ retouched images, and a 60-second highlight reel.","pt":"FIXED","lo":14000,"hi":28000},
         {"n":"Half-Day Event Coverage","d":"Six hours of coverage, two photographers and one cinematographer, all edited high-res photos in an online gallery, and a three-minute same-day-edit film.","pt":"FIXED","lo":35000,"hi":62000},
         {"n":"Full-Day Wedding Coverage","d":"Twelve hours of coverage, lead and second shooter, drone, same-day edit, 400+ edited photos, a cinematic feature film, and a printed lay-flat album.","pt":"RANGE","lo":80000,"hi":150000},
         {"n":"Add-On: Photo Booth and Extra Hours","d":"On-site photo booth with unlimited prints plus additional coverage hours and raw-file turnover. Priced to your final timeline.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"PHOTO_BOOTHS","weight":15,"ptype":"SERVICE",
       "cores":["Photo Booth Co.","Booth Rentals","Snap Booth","Party Booth","Instant Prints","Booth and Print Co.","Kodakan Booth","Selfie Station"],
       "packages":[
         {"n":"Classic Print Booth - 3 hours","d":"Open-style photo booth for three hours, unlimited 2x6 strip prints, props, a custom print layout, an on-site attendant, and a digital gallery.","pt":"FIXED","lo":12000,"hi":20000},
         {"n":"360 Video Booth - 3 hours","d":"360-degree spinning platform with slow-motion video, ring light, props, an instant-sharing station, and an attendant.","pt":"FIXED","lo":18000,"hi":30000},
         {"n":"Party Package - Booth + 360 + Guestbook","d":"Print booth and 360 booth together for four hours, a scrapbook guestbook, a USB of all files, and two attendants.","pt":"RANGE","lo":28000,"hi":45000},
         {"n":"Corporate / Multi-Day Booth Package","d":"Branded booths for activations, conferences, and mall events. Units, hours, and branding quoted per activation.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"EVENT_COORDINATOR","weight":32,"ptype":"SERVICE",
       "cores":["Events and Co.","Event Styling and Coordination","Weddings and Events","Event Management","Occasions","Celebrations Co.","Event Planners","Kasalan Planners","Momentous Events","Detalye Events"],
       "packages":[
         {"n":"On-the-Day Coordination","d":"Final-month planning turnover, supplier confirmation, a detailed timeline, ingress and egress management, and a team of two coordinators on the event day.","pt":"FIXED","lo":25000,"hi":45000},
         {"n":"Partial Planning and Styling","d":"Everything in on-the-day coordination plus supplier sourcing, budget tracking, a styling concept and mood board, and two planning meetings.","pt":"FIXED","lo":55000,"hi":95000},
         {"n":"Full Planning and Design","d":"End-to-end planning from concept to execution: venue and supplier selection, full design direction, RSVP management, and a lead planner plus a three-person team.","pt":"RANGE","lo":120000,"hi":250000},
         {"n":"Corporate and Milestone Events","d":"Conferences, product launches, galas, and anniversaries. Scope, run-of-show, and manpower quoted per brief.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"EVENT_HOST","weight":11,"ptype":"SERVICE",
       "cores":["Hosts and Emcees","The Host Co.","Emcee Services","On-Mic Hosts","Event Hosts","Program Hosts","Master of Ceremonies"],
       "packages":[
         {"n":"Half-Day Hosting","d":"A professional emcee for up to four hours, one alignment meeting, script collaboration, and program-flow management.","pt":"FIXED","lo":8000,"hi":18000},
         {"n":"Full Wedding Hosting","d":"Ceremony and reception hosting, script writing, coordination with the couple and coordinator, and games facilitation.","pt":"FIXED","lo":15000,"hi":30000},
         {"n":"Corporate Program Hosting","d":"Conference or awards-night hosting, a teleprompter-ready script, a run-through with the AVP team, and a backup host on standby.","pt":"RANGE","lo":25000,"hi":50000},
         {"n":"Bilingual / Celebrity-Style Host","d":"Sought-after hosts for large corporate and society events. Rate quoted per date and program length.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"ENTERTAINMENT","weight":24,"ptype":"SERVICE",
       "cores":["Entertainment","Live Music and Entertainment","Sound and Show","Band and DJ Co.","Stage Entertainment","Party Entertainment","Tugtugan Entertainment"],
       "packages":[
         {"n":"Acoustic Duo","d":"A two-piece acoustic act for two 45-minute sets, own basic PA for up to 80 guests, and a curated song list you approve.","pt":"FIXED","lo":15000,"hi":28000},
         {"n":"Party Band - 4 Sets","d":"A five-piece band with vocalist, three to four sets across the night, a full band PA, and song requests accommodated.","pt":"FIXED","lo":45000,"hi":85000},
         {"n":"Band + DJ Night Package","d":"Live band for the reception and a DJ for the after-party, MC coordination, lights, and a dance-floor sound system.","pt":"RANGE","lo":90000,"hi":160000},
         {"n":"Headline Act / Custom Production","d":"Featured artists, show bands, and full stage productions. Talent fee and technical rider quoted per event.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"PERFORMERS","weight":9,"ptype":"SERVICE",
       "cores":["Performers Guild","Stage Acts","Cirque and Co.","Roving Performers","Dance Company","Variety Acts","Palabas Performers"],
       "packages":[
         {"n":"Roving Act - 2 hours","d":"Two roving performers (stilt-walkers, LED robots, or magicians) for two hours, costumes, and a coordinator.","pt":"FIXED","lo":12000,"hi":25000},
         {"n":"Production Number","d":"A choreographed six-dancer production number, one custom routine, costumes, and two rehearsals with a music edit.","pt":"FIXED","lo":25000,"hi":50000},
         {"n":"Full Stage Show - 45 min","d":"A multi-act variety show (aerial, fire, dance, magic) with technical direction, costumes, and a show-caller.","pt":"RANGE","lo":55000,"hi":110000},
         {"n":"Themed / Custom Cast","d":"Character casts and themed ensembles built around your concept. Cast size and set list quoted per brief.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"DECORATION_PRODUCTION","weight":18,"ptype":"SERVICE",
       "cores":["Styling and Decor","Event Decor Co.","Design and Production","Stylist Studio","Decor and Draping","Set and Scenic","Palamuti Styling","Bloom and Build"],
       "packages":[
         {"n":"Ceremony Styling","d":"Aisle and altar styling, welcome signage, floral or dried arrangements, and setup and teardown.","pt":"FIXED","lo":25000,"hi":50000},
         {"n":"Reception Styling - Standard","d":"A stage backdrop, the couple's table, guest centerpieces for up to 15 tables, an entrance arch, and lighting accents.","pt":"FIXED","lo":60000,"hi":120000},
         {"n":"Full-Room Design and Build","d":"A custom-fabricated backdrop, ceiling treatment, lounge setup, a full floral program, and a production crew for ingress and egress.","pt":"RANGE","lo":150000,"hi":320000},
         {"n":"Themed / Corporate Set Production","d":"Concept-driven set design and fabrication for launches, galas, and shows. Renders and build quoted per concept.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"LIGHTS_AND_SOUNDS","weight":16,"ptype":"SERVICE",
       "cores":["Lights and Sounds","AV Productions","Sound and Lighting Co.","Stage Tech","Audio Visual","Ilaw at Tunog AV","Live Sound Rentals"],
       "packages":[
         {"n":"Basic Party Set","d":"A PA system for up to 100 guests, two wireless mics, four par-can lights, a small mixer, and an operator.","pt":"FIXED","lo":15000,"hi":30000},
         {"n":"Reception AV Package","d":"A line-array PA for up to 250 guests, a wireless mic set, moving-head lighting, haze, a projector and screen, and a two-person crew.","pt":"FIXED","lo":35000,"hi":70000},
         {"n":"Concert-Grade Production","d":"A full stage PA, monitors, a lighting rig with console, LED par wash, backline, and a technical crew with an engineer.","pt":"RANGE","lo":90000,"hi":200000},
         {"n":"LED Wall + Full Production","d":"Large-format audio, lighting, and an LED wall for concerts and corporate shows. System design quoted per venue.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"FOOD_CARTS_GRAZING","weight":11,"ptype":"SERVICE","cap":[50,300],
       "cores":["Food Carts PH","Mobile Snack Co.","Cart Concessions","Merienda Carts","Snack Station Co.","Kariton Food Carts","Grazing and Carts"],
       "packages":[
         {"n":"Single Cart - 3 hours","d":"One food cart (popcorn, cotton candy, fries, or nachos) for three hours, unlimited servings for up to 100 guests, crew, and supplies.","pt":"FIXED","lo":6000,"hi":12000},
         {"n":"Triple Cart Combo","d":"Any three carts for four hours, unlimited servings for up to 150 guests, themed cart skirting, and uniformed crew.","pt":"FIXED","lo":15000,"hi":28000},
         {"n":"Snack Bar Buffet - 5 stations","d":"A five-station snack bar (savory and sweet) for up to 200 guests, styling, signage, and a serving team for four hours.","pt":"RANGE","lo":30000,"hi":55000},
         {"n":"Grazing Table + Beverage Bar","d":"A styled grazing table and drinks bar sized to your guest count. Spread and stations quoted per event.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"SOUVENIR_GIVEAWAYS","weight":8,"ptype":"PRODUCT",
       "cores":["Souvenirs and Tokens","Giveaway Co.","Pasalubong Crafts","Keepsakes","Party Favors PH","Alaala Souvenirs","Token and Trinket"],
       "packages":[
         {"n":"Standard Favors - 50 pcs","d":"Fifty personalized favors (scented candles, mini succulents, or ref magnets), custom tags, and gift packaging.","pt":"FIXED","lo":5000,"hi":12000},
         {"n":"Premium Favors - 100 pcs","d":"One hundred curated favors with a custom box, ribbon, and printed thank-you card, plus a display basket.","pt":"FIXED","lo":12000,"hi":25000},
         {"n":"Corporate Tokens - 150 pcs","d":"One hundred fifty branded tokens (tumblers, tote bags, or desk items), one-color logo print, and individual sleeves.","pt":"RANGE","lo":25000,"hi":60000},
         {"n":"Bespoke / Bulk Giveaways","d":"Custom-designed souvenirs at volume. Materials, branding, and lead time quoted per order.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"CAKE_AND_PASTRIES","weight":20,"ptype":"PRODUCT","cap":[40,250],
       "cores":["Cakes and Pastries","Bakeshop","Patisserie","Cake Studio","Sweet Studio","Panaderia and Cakes","Sugar and Flour","Dessert Atelier"],
       "packages":[
         {"n":"Celebration Cake - 2 tiers","d":"A two-tier buttercream or fondant cake serving up to 60, one custom design, one flavor, and a cake tasting for two.","pt":"FIXED","lo":4500,"hi":9000},
         {"n":"Wedding Cake - 3 tiers + faux","d":"Three real tiers plus a faux display serving up to 120, a custom design, two flavors, delivery, and setup.","pt":"FIXED","lo":9000,"hi":20000},
         {"n":"Dessert Table - 5 varieties","d":"A styled dessert table with five treats (cupcakes, verrines, cookies, macarons, mini tarts) for up to 150, plus stands and signage.","pt":"RANGE","lo":20000,"hi":45000},
         {"n":"Grand Dessert Spread / Corporate Orders","d":"Large dessert bars and branded pastry boxes for corporate events. Assortment and volume quoted per order.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"INFLATABLES","weight":4,"ptype":"SERVICE","cap":[20,150],
       "cores":["Inflatables and Play","Bounce Co.","Party Inflatables","Jump and Slide Rentals","Fun House Inflatables","Kids Bounce PH"],
       "packages":[
         {"n":"Bouncy Castle - 4 hours","d":"One themed bouncy castle (up to 12x12 ft) for four hours, a blower, safety mats, delivery, setup, and an attendant.","pt":"FIXED","lo":6000,"hi":12000},
         {"n":"Combo Unit - Slide + Bounce","d":"A combo inflatable with a slide and obstacle for four hours, a safety marshal, and a generator if no power is available.","pt":"FIXED","lo":10000,"hi":20000},
         {"n":"Fun Zone - 3 units","d":"Three inflatables (castle, slide, and interactive game) for five hours, two marshals, mats, and cordon setup.","pt":"RANGE","lo":22000,"hi":40000},
         {"n":"Water / Custom Inflatable Park","d":"Water slides and large inflatable parks for fiestas and fairs. Units, area, and manpower quoted per venue.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"MOBILE_PLAYGROUND","weight":4,"ptype":"SERVICE","cap":[10,80],
       "cores":["Mobile Playground","Play Bus PH","Roving Play Co.","Kiddie Play Rentals","Soft Play and Ball Pits","Laro Mobile Play"],
       "packages":[
         {"n":"Soft Play Set - 3 hours","d":"Padded soft-play equipment and a ball pit for toddlers, safety fencing, floor mats, and one play attendant for three hours.","pt":"FIXED","lo":7000,"hi":14000},
         {"n":"Play Corner + Mini Trampoline","d":"Soft play, a ball pit, a mini trampoline, and ride-on toys for four hours with two attendants.","pt":"FIXED","lo":12000,"hi":22000},
         {"n":"Full Mobile Playground","d":"A complete roving playground (soft play, ball pit, slides, ride-ons, sensory toys) for five hours with three attendants and setup.","pt":"RANGE","lo":24000,"hi":45000},
         {"n":"School / Mall Activation Package","d":"Recurring play setups for schools, malls, and fairs. Schedule and equipment quoted per run.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"ARCADE","weight":4,"ptype":"SERVICE","cap":[20,200],
       "cores":["Arcade Rentals","Retro Arcade Co.","Game Room PH","Coin-Op Rentals","Play and Win Arcade","Barkada Arcade"],
       "packages":[
         {"n":"3-Machine Set - 4 hours","d":"Three arcade machines (basketball, air hockey, or classic cabinets) on free-play for four hours, delivery, setup, and an attendant.","pt":"FIXED","lo":9000,"hi":18000},
         {"n":"Game Room - 6 machines","d":"Six machines including a claw machine and a racing seat on free-play for five hours, with two attendants.","pt":"FIXED","lo":18000,"hi":35000},
         {"n":"Full Arcade Takeover - 12 machines","d":"Twelve mixed machines, prize-claw stock, tokens or free-play, signage, and a three-person crew for six hours.","pt":"RANGE","lo":40000,"hi":80000},
         {"n":"Corporate / Long-Term Arcade Hire","d":"Multi-day arcade setups for company events and pop-ups. Machine mix and duration quoted per booking.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"INVITATIONS","weight":12,"ptype":"PRODUCT",
       "cores":["Invitations and Print","Paper and Press","Invite Studio","Letterpress Co.","Stationery Atelier","Imbitasyon Press","Ink and Fold"],
       "packages":[
         {"n":"Digital Invitation Suite","d":"A custom-designed e-invite with a matching RSVP page, a save-the-date graphic, and two rounds of revisions.","pt":"FIXED","lo":3500,"hi":8000},
         {"n":"Classic Printed Set - 100 pcs","d":"One hundred printed invitations with envelope, insert card, and belly band; one design concept and two revisions.","pt":"FIXED","lo":12000,"hi":28000},
         {"n":"Premium Suite - 100 pcs","d":"One hundred suites with specialty paper, a foil or letterpress accent, a wax seal, a vellum wrap, and assembly.","pt":"RANGE","lo":30000,"hi":70000},
         {"n":"Full Stationery Program","d":"Invitations plus on-the-day paper goods (menus, place cards, signage, programs). Quantities and finishes quoted per set.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"HAIR_AND_MAKEUP","weight":20,"ptype":"SERVICE",
       "cores":["Hair and Makeup","Glam Studio","Makeup Artistry","Beauty Team","Bridal Glam Co.","Ganda Studio","Brush and Blush"],
       "packages":[
         {"n":"Party Glam - 1 face","d":"One full hair and makeup application for an event guest or debutante, false lashes included, at the studio or on location.","pt":"FIXED","lo":2500,"hi":6000},
         {"n":"Bridal Trial + Wedding Day","d":"One pre-wedding trial plus wedding-day hair and makeup for the bride, an airbrush option, lashes, and a touch-up kit.","pt":"FIXED","lo":12000,"hi":25000},
         {"n":"Bride + Entourage of 5","d":"The bride plus five glam sessions, one artist and one hair stylist, on-location service, and a standby touch-up artist.","pt":"RANGE","lo":28000,"hi":55000},
         {"n":"Large Entourage / Editorial","d":"Big entourages, production shoots, and multi-day coverage. Team size and call times quoted per booking.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"BRIDAL_GOWN_DESIGNER","weight":6,"ptype":"SERVICE",
       "cores":["Bridal Couture","Gown Atelier","Bridal Designs","The Gown Room","Couture Bridal Studio","Terno Bridal"],
       "packages":[
         {"n":"Off-Rack Rental","d":"Rental of one ready gown from the current collection, standard alterations for fit, steaming, and a garment bag.","pt":"FIXED","lo":15000,"hi":35000},
         {"n":"Made-to-Measure Gown","d":"A custom gown built to the bride's measurements from an existing design, three fittings, and delivery.","pt":"FIXED","lo":45000,"hi":110000},
         {"n":"Bespoke Couture Gown","d":"A fully bespoke design from sketch, premium fabrics and beadwork, four to five fittings, and a custom veil.","pt":"RANGE","lo":120000,"hi":300000},
         {"n":"Entourage and Mothers' Gowns","d":"Coordinated gowns for the maids and mothers of the couple. Fabric, count, and timeline quoted per party.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"SUIT_RENTALS","weight":6,"ptype":"SERVICE",
       "cores":["Suit and Barong Rentals","The Suit Bar","Formalwear Co.","Barong Atelier","Amerikana Rentals","Gentlemens Rentals"],
       "packages":[
         {"n":"Single Suit / Barong Rental","d":"One suit or pineapple-blend barong with trousers, fitting and alterations, steaming, and a garment bag.","pt":"FIXED","lo":2500,"hi":6000},
         {"n":"Groom's Package","d":"A premium three-piece suit or a hand-embroidered barong for the groom, two fittings, accessories, and shoes.","pt":"FIXED","lo":6000,"hi":14000},
         {"n":"Groom + Groomsmen of 6","d":"The groom plus six coordinated sets, a group fitting session, alterations, and pressing before the event.","pt":"RANGE","lo":18000,"hi":40000},
         {"n":"Full Entourage and Fathers","d":"Matching formalwear for the full male entourage and fathers. Sizes, fabric, and count quoted per party.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"WARDROBE_STYLISTS_DRESSERS","weight":5,"ptype":"SERVICE",
       "cores":["Wardrobe Styling","Style and Dress Co.","On-Set Stylists","The Dresser","Fashion Direction PH","Damit Stylists"],
       "packages":[
         {"n":"Personal Styling Session","d":"One two-hour consultation, event-look curation, a sourcing shortlist, and a fitting.","pt":"FIXED","lo":5000,"hi":12000},
         {"n":"Event-Day Dresser","d":"One dresser on-site for the bride or celebrant, outfit-change assistance, steaming, and an emergency kit for eight hours.","pt":"FIXED","lo":10000,"hi":22000},
         {"n":"Family / Entourage Styling","d":"Coordinated looks for up to eight people, palette direction, sourcing, fittings, and two dressers on the day.","pt":"RANGE","lo":25000,"hi":55000},
         {"n":"Production / Editorial Styling","d":"Wardrobe direction for shoots, shows, and campaigns. Pull budget and team quoted per production.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"POWER_GENERATOR_SERVICES","weight":4,"ptype":"SERVICE",
       "cores":["Power and Generator Rentals","Genset Solutions","Standby Power Co.","Event Power PH","Kuryente Genset Rentals"],
       "packages":[
         {"n":"15 kVA Standby Genset","d":"A silent 15 kVA generator for up to eight hours, fuel for the duration, delivery, and an on-site operator.","pt":"FIXED","lo":12000,"hi":22000},
         {"n":"60 kVA Event Genset","d":"A 60 kVA soundproof generator with a distribution board and cabling, delivery, and an operator for ten hours.","pt":"FIXED","lo":25000,"hi":45000},
         {"n":"Dual-Genset Redundant Setup","d":"Two synchronized gensets for automatic failover, full cable runs, a distro, and two operators for large productions.","pt":"RANGE","lo":55000,"hi":110000},
         {"n":"Multi-Day / Festival Power Plan","d":"Power design and multiple units for festivals and long builds. Load study and manpower quoted per site.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"LED_WALL_VISUAL_PROJECTION_RENTALS","weight":5,"ptype":"SERVICE",
       "cores":["LED Wall Rentals","Visual Display Co.","Pixel and Projection","Screen Solutions PH","Big Screen Rentals"],
       "packages":[
         {"n":"Projector + Screen Set","d":"A 10,000-lumen projector with a 10-ft screen, a laptop switcher, cabling, and a technician for the event.","pt":"FIXED","lo":12000,"hi":25000},
         {"n":"3x2 m Indoor LED Wall","d":"An indoor LED wall (about 3x2 m), a processor, content playback, rigging or ground-stack, and an operator.","pt":"FIXED","lo":35000,"hi":70000},
         {"n":"6x3 m LED Wall + Playback","d":"A large LED wall with a media server, a confidence monitor, content QC, and a two-person crew for ingress and show.","pt":"RANGE","lo":90000,"hi":180000},
         {"n":"Outdoor / Multi-Screen Production","d":"Weatherproof walls and multi-screen setups for concerts and rallies. Pixel pitch and rigging quoted per venue.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"STAGING_TRUSSING_FLOORING_RENTALS","weight":5,"ptype":"SERVICE",
       "cores":["Staging and Trussing","Stage Systems PH","Truss and Deck Rentals","Platform and Stage Co.","Entablado Staging"],
       "packages":[
         {"n":"Low Stage - 4x6 m","d":"Twenty-four square meters of decked stage at 0.4 m height, skirting, steps, and setup and teardown.","pt":"FIXED","lo":15000,"hi":30000},
         {"n":"Stage + Goalpost Truss","d":"A 6x8 m stage at 0.6 m, a goalpost truss for lights and a backdrop, safety rails, and a build crew.","pt":"FIXED","lo":35000,"hi":70000},
         {"n":"Full Stage, Truss Roof and Ramps","d":"A large stage with a ground-support truss roof, wings, ramps, barricades, and an engineered build with crew.","pt":"RANGE","lo":90000,"hi":190000},
         {"n":"Custom Scenic / Runway Builds","d":"Runways, multi-level stages, and scenic platforms. Structural design and crew quoted per plan.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"TRANSPORT_SHUTTLE_FLEET_SERVICES","weight":5,"ptype":"SERVICE",
       "cores":["Shuttle and Transport","Fleet Services PH","Event Transport Co.","Guest Shuttle Solutions","Biyahe Fleet Services"],
       "packages":[
         {"n":"Bridal Car - 6 hours","d":"One well-kept sedan or classic car with a driver for six hours, fuel, a ribbon and floral accent, and standby time.","pt":"FIXED","lo":6000,"hi":14000},
         {"n":"Guest Shuttle - Coaster","d":"One 28-seat coaster with a driver for eight hours, two trips within Metro Manila, fuel, and coordination with your timeline.","pt":"FIXED","lo":12000,"hi":24000},
         {"n":"Fleet Package - 3 vehicles","d":"A bridal car plus two shuttles for the day, a routing plan, a dispatch coordinator, and fuel for city routes.","pt":"RANGE","lo":35000,"hi":75000},
         {"n":"Multi-Day / Out-of-Town Fleet","d":"Provincial runs and multi-vehicle logistics. Routes, overnight, and vehicle mix quoted per itinerary.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"SECURITY_CROWD_CONTROL","weight":4,"ptype":"SERVICE",
       "cores":["Security and Crowd Control","Event Safety PH","Guarding Solutions","Marshal Services","Bantay Event Security"],
       "packages":[
         {"n":"4 Guards - 8 hours","d":"Four licensed guards for one eight-hour shift, one team leader, handheld radios, and a pre-event briefing.","pt":"FIXED","lo":8000,"hi":16000},
         {"n":"10-Guard Detail + Marshals","d":"Ten guards and four crowd marshals, bag inspection at entry, roving patrol, and a supervisor for a ten-hour call.","pt":"FIXED","lo":18000,"hi":35000},
         {"n":"Large-Event Security Plan","d":"Twenty-plus personnel, access-control tiers, VIP close-in, a comms plan, and coordination with venue and local authorities.","pt":"RANGE","lo":45000,"hi":95000},
         {"n":"Concert / Festival Security","d":"Full security planning for mass-gathering events. Headcount, K9, and command setup quoted per risk assessment.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"INTERACTIVE_BAR_MIXOLOGY_SERVICES","weight":5,"ptype":"SERVICE",
       "cores":["Mobile Bar and Mixology","The Cocktail Cart","Craft Bar Co.","Mixology Collective","Inuman Mobile Bar"],
       "packages":[
         {"n":"Mobile Bar - 3 hours","d":"A styled mobile bar, one bartender, two signature cocktails plus soft drinks for up to 80 guests, glassware, ice, and garnishes.","pt":"FIXED","lo":15000,"hi":30000},
         {"n":"Open Bar - 4 hours, 120 pax","d":"Two bartenders, four cocktails plus beer and wine, premium spirits, bar styling, and full setup for 120 guests.","pt":"FIXED","lo":35000,"hi":65000},
         {"n":"Premium Mixology Experience","d":"A craft cocktail program with tableside service, smoke and clarified drinks, three bartenders, and a menu tasting.","pt":"RANGE","lo":70000,"hi":140000},
         {"n":"Corporate / Branded Bar Activation","d":"Branded bars and themed drink menus for launches and expos. Consumption and branding quoted per activation.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"LIVE_EVENT_PAINTERS_SKETCH_ARTISTS","weight":4,"ptype":"SERVICE",
       "cores":["Live Painting Studio","Event Sketch Artists","Brushstroke Live","On-Site Portraits","Guhit Live Art"],
       "packages":[
         {"n":"Live Sketch Station - 3 hours","d":"One caricature or portrait artist for three hours, roughly 20 to 30 guest sketches, paper, and an easel setup.","pt":"FIXED","lo":10000,"hi":20000},
         {"n":"Live Event Painting","d":"One artist paints the scene live on a 24x36 in canvas during the event; the finished piece is varnished and handed over on the day.","pt":"FIXED","lo":25000,"hi":50000},
         {"n":"Painting + 2 Sketch Artists","d":"Live event painting plus two sketch stations for guest keepsakes, all materials, and framed turnover of the main piece.","pt":"RANGE","lo":45000,"hi":85000},
         {"n":"Mural / Corporate Live Art","d":"Large live murals and branded art activations. Size, surface, and turnover quoted per brief.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"SPECIAL_EFFECTS","weight":5,"ptype":"SERVICE",
       "cores":["Special Effects Co.","FX and Pyro","Effects Lab PH","Stage FX Rentals","Ispesyal Effects"],
       "packages":[
         {"n":"Indoor Effects Set","d":"Two cold-spark machines and a low-lying dry-ice fog for the first dance or entrance, an operator, and a safety check.","pt":"FIXED","lo":12000,"hi":25000},
         {"n":"Reception FX Package","d":"Cold sparks, CO2 jets, a confetti blast, and a dancing-on-the-clouds effect, with an operator and consumables.","pt":"FIXED","lo":30000,"hi":60000},
         {"n":"Full Show FX Production","d":"Programmed effects across the show (pyro-look, flames, streamers, haze), DMX control, and a licensed crew.","pt":"RANGE","lo":70000,"hi":150000},
         {"n":"Outdoor Pyrotechnics / Custom","d":"Licensed outdoor pyromusical and large-scale effects. Design, permits, and crew quoted per site.","pt":"QUOTE","lo":0,"hi":0}
       ]},

      {"type":"FLORAL_SERVICES","weight":16,"ptype":"SERVICE",
       "cores":["Florals and Botanicals","Flower Studio","Bloom Co.","Petal and Stem","Floral Design House","Bulaklak Studio","Wildflower and Co."],
       "packages":[
         {"n":"Bridal Flowers Set","d":"A bridal bouquet, three entourage bouquets, four boutonnieres, two corsages, and delivery on the day.","pt":"FIXED","lo":12000,"hi":28000},
         {"n":"Ceremony + Reception Florals","d":"Aisle arrangements, altar or arch florals, a couple's-table runner, and centerpieces for up to 12 guest tables.","pt":"FIXED","lo":45000,"hi":95000},
         {"n":"Full Floral Design","d":"Complete floral styling with statement installations, a ceiling or arch build, lounge florals, and a setup and teardown team.","pt":"RANGE","lo":120000,"hi":260000},
         {"n":"Corporate / Event Florals","d":"Stage, lobby, and table florals for launches and galas. Palette and volume quoted per event.","pt":"QUOTE","lo":0,"hi":0}
       ]}
    ]
    $J$::jsonb;

    v_fnames text[] := ARRAY['Maria','Jose','Juan','Ana','Antonio','Rosa','Ramon','Carmen','Andres','Luz',
        'Ricardo','Elena','Manuel','Teresa','Eduardo','Cristina','Francisco','Gloria','Miguel','Divina',
        'Rodrigo','Josefina','Ernesto','Corazon','Alfredo','Lourdes','Danilo','Imelda','Roberto','Beatriz',
        'Paolo','Kristine','Marco','Angelica','Rafael','Bea','Carlo','Nicole','Mark','Patricia',
        'John Paul','Camille','Christian','Andrea','Joshua','Trisha','Kevin','Alyssa','Bryan','Erika'];
    v_lnames text[] := ARRAY['Santos','Reyes','Cruz','Bautista','Ocampo','Garcia','Mendoza','Torres','Flores','Villanueva',
        'Ramos','Del Rosario','Aquino','Castillo','Navarro','Salvador','Gonzales','Fernandez','Domingo','Aguilar',
        'Rivera','Mercado','Pascual','Tan','Lim','Sy','Uy','Chua','Dizon','Manalo',
        'Gutierrez','Rosales','Alonzo','Espiritu','Padilla','Marasigan','Bringas','Concepcion','Diaz','Enriquez'];
    v_prefixes text[] := ARRAY['Manila','Metro','Grand','Golden','Royal','Blissful','Timeless','Enchanted','Luxe','Prime',
        'Classic','Elegant','Rustic','Modern','Signature','Pearl','Sampaguita','Narra','Bayanihan','Fiesta',
        'Araw','Bituin','Alab','Habi','Silahis','Tala','Liwanag','Ginto','Haraya','Dakila'];
    v_cities text[] := ARRAY['Makati|Metro Manila (NCR)','Quezon City|Metro Manila (NCR)','Pasig|Metro Manila (NCR)',
        'Taguig|Metro Manila (NCR)','Mandaluyong|Metro Manila (NCR)','Paranaque|Metro Manila (NCR)',
        'Cebu City|Cebu','Mandaue|Cebu','Lapu-Lapu|Cebu','Davao City|Davao del Sur','Tagaytay|Cavite',
        'Bacoor|Cavite','Imus|Cavite','Dasmarinas|Cavite','Antipolo|Rizal','Taytay|Rizal','Calamba|Laguna',
        'Santa Rosa|Laguna','Binan|Laguna','Angeles|Pampanga','San Fernando|Pampanga','Baguio|Benguet',
        'Iloilo City|Iloilo','Bacolod|Negros Occidental','Lipa|Batangas','Batangas City|Batangas',
        'Naga|Camarines Sur','Malolos|Bulacan','Meycauayan|Bulacan','Cabanatuan|Nueva Ecija'];
    v_prov_pool text[] := ARRAY['Metro Manila (NCR)','Cavite','Laguna','Rizal','Bulacan','Batangas','Pampanga','Cebu',
        'Davao del Sur','Iloilo','Negros Occidental','Benguet','Pangasinan','Nueva Ecija','Quezon','Camarines Sur',
        'Bataan','Tarlac','La Union','Palawan','Albay','Leyte','Misamis Oriental','Zambales','Nueva Vizcaya'];
    v_all_types text[] := ARRAY['WEDDING','ANNIVERSARY','BIRTHDAY','PARTY','BABY_SHOWER','BRIDAL_SHOWER','BACHELOR_PARTY',
        'BACHELORETTE_PARTY','ENGAGEMENT_PARTY','GRADUATION','RETIREMENT','REUNION','HOUSEWARMING','SEMINAR',
        'NETWORKING_EVENT','PRODUCT_LAUNCH','TEAM_BUILDING','CORPORATE_RETREAT','TRADE_SHOW','GALA','FUNDRAISER',
        'CONCERT','FESTIVAL','EXHIBITION','SPORTS_EVENT','OTHER'];
    v_focus text[] := ARRAY['reliable setup and on-time delivery','clear communication and detailed planning',
        'premium materials and a clean finish','flexible packages for any budget',
        'an experienced crew and smooth execution','a personalized approach for every client',
        'transparent pricing with no hidden costs','calm, organized coordination on the day'];

    v_n_cfg int := jsonb_array_length(v_config);
    v_i int; v_k int; v_assigned int := 0; v_this int; v_seq int := 0;
    v_cfg jsonb; v_pkg jsonb;
    v_type text; v_label text; v_ptype text;
    v_cores text[]; v_core text; v_pat int;
    v_fname text; v_lname text; v_bn text; v_slug text;
    v_citypair text; v_city text; v_prov text;
    v_desc text; v_overview text;
    v_verified boolean; v_top boolean;
    v_cap int; v_base numeric;
    v_user_id bigint; v_vp_id bigint;
    v_types text[]; v_extra text[]; v_pt text; v_lo numeric; v_hi numeric;
    v_price numeric; v_min numeric; v_max numeric;
    v_years int; v_events int;
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE google_id LIKE 'seedv-%') THEN
        RAISE EXCEPTION 'Seed vendors already present - run scripts/unseed-vendors.sql first.';
    END IF;

    PERFORM setseed(0.42);

    FOR v_i IN 0 .. v_n_cfg - 1 LOOP
        v_cfg   := v_config -> v_i;
        v_type  := v_cfg ->> 'type';
        v_ptype := COALESCE(v_cfg ->> 'ptype', 'SERVICE');
        v_label := lower(replace(replace(v_type, '_AND_', ' and '), '_', ' '));

        SELECT array_agg(x) INTO v_cores FROM jsonb_array_elements_text(v_cfg -> 'cores') x;

        IF v_i = v_n_cfg - 1 THEN
            v_this := v_target - v_assigned;
        ELSE
            v_this := round((v_cfg ->> 'weight')::numeric * v_target / 500.0);
        END IF;
        IF v_this < 0 THEN v_this := 0; END IF;
        v_assigned := v_assigned + v_this;

        v_base := (v_cfg -> 'packages' -> 1 ->> 'lo')::numeric;

        FOR v_k IN 1 .. v_this LOOP
            v_seq   := v_seq + 1;
            v_fname := v_fnames[1 + floor(random() * array_length(v_fnames, 1))::int];
            v_lname := v_lnames[1 + floor(random() * array_length(v_lnames, 1))::int];
            v_core  := v_cores[1 + floor(random() * array_length(v_cores, 1))::int];
            v_pat   := floor(random() * 3)::int;
            IF v_pat = 0 THEN
                v_bn := v_prefixes[1 + floor(random() * array_length(v_prefixes, 1))::int] || ' ' || v_core;
            ELSIF v_pat = 1 THEN
                v_bn := v_core || ' by ' || v_fname;
            ELSE
                v_bn := v_lname || ' ' || v_core;
            END IF;

            v_slug := trim(both '-' from left(regexp_replace(lower(v_bn), '[^a-z0-9]+', '-', 'g'), 180))
                      || '-' || v_seq::text;

            v_citypair := v_cities[1 + floor(random() * array_length(v_cities, 1))::int];
            v_city := split_part(v_citypair, '|', 1);
            v_prov := split_part(v_citypair, '|', 2);

            v_years  := 2 + floor(random() * 14)::int;
            v_events := 40 + floor(random() * 460)::int;
            v_desc := v_bn || ' is a ' || v_label || ' provider based in ' || v_city || ', ' || v_prov
                      || '. We handle weddings, debuts, corporate events, and private celebrations, and we serve clients nationwide.';
            v_overview := 'With ' || v_years || ' years in the industry, ' || v_bn || ' has delivered ' || v_events
                      || '+ events. Our team is known for ' || v_focus[1 + floor(random() * array_length(v_focus, 1))::int] || '.';

            v_verified := random() < 0.40;
            v_top      := random() < 0.08;

            IF v_cfg -> 'cap' IS NOT NULL THEN
                v_cap := (v_cfg -> 'cap' ->> 0)::int
                         + floor(random() * (((v_cfg -> 'cap' ->> 1)::int) - ((v_cfg -> 'cap' ->> 0)::int) + 1))::int;
            ELSE
                v_cap := NULL;
            END IF;

            INSERT INTO users (google_id, email, first_name, last_name, role, terms_accepted_at, terms_version, created_at, updated_at)
            VALUES ('seedv-' || lpad(v_seq::text, 7, '0'),
                    'seed.vendor.' || v_seq || '@seed.eventsrus.test',
                    v_fname, v_lname, 'VENDOR', now(), '2026-09-03', now(), now())
            RETURNING id INTO v_user_id;

            INSERT INTO vendor_profiles (user_id, owner_name, slug, referral_code, description, contact_email, phone_number,
                    city, state, country, verified, verified_at, verified_by_admin, top_vendor,
                    business_name, business_type, primary_category, max_guest_capacity, base_price, lead_time_days,
                    storefront_overview, created_at, updated_at)
            VALUES (v_user_id, v_fname || ' ' || v_lname, v_slug, 'SDV' || lpad(v_seq::text, 7, '0'), v_desc,
                    'inquiries.' || v_seq || '@seed.eventsrus.test',
                    '+63 9' || lpad(floor(random() * 100)::int::text, 2, '0') || ' '
                        || lpad(floor(random() * 1000)::int::text, 3, '0') || ' '
                        || lpad(floor(random() * 10000)::int::text, 4, '0'),
                    v_city, v_prov, 'Philippines',
                    v_verified,
                    CASE WHEN v_verified THEN now() - (floor(random() * 300)::int::text || ' days')::interval ELSE NULL END,
                    CASE WHEN v_verified THEN 'seed-script' ELSE NULL END,
                    v_top, v_bn, v_type, v_type, v_cap, v_base, floor(random() * 22)::int,
                    v_overview, now(), now())
            RETURNING id INTO v_vp_id;

            INSERT INTO vendor_subscriptions (user_id, current_period_start, current_period_end, plan, billing_source, status, created_at, updated_at)
            VALUES (v_user_id, now() - interval '30 days', now() + interval '3650 days', 'PRO', 'FREE_GRANT', 'ACTIVE', now(), now());

            INSERT INTO vendor_operating_areas (vendor_profile_id, area) VALUES (v_vp_id, 'Entire Philippines');
            INSERT INTO vendor_operating_areas (vendor_profile_id, area)
            SELECT v_vp_id, p FROM (
                SELECT p FROM unnest(v_prov_pool) AS p ORDER BY random() LIMIT (1 + floor(random() * 3)::int)
            ) q;

            -- Bias toward the common celebrations (most PH event vendors do take
            -- weddings), then top up to a random 4-10 total with other types so
            -- the event-type search filter still has something to bite on.
            v_types := ARRAY[]::text[];
            IF random() < 0.92 THEN v_types := array_append(v_types, 'WEDDING');     END IF;
            IF random() < 0.75 THEN v_types := array_append(v_types, 'BIRTHDAY');    END IF;
            IF random() < 0.65 THEN v_types := array_append(v_types, 'ANNIVERSARY'); END IF;
            IF random() < 0.60 THEN v_types := array_append(v_types, 'PARTY');       END IF;
            SELECT array_agg(t) INTO v_extra FROM (
                SELECT t FROM unnest(v_all_types) AS t
                WHERE NOT (t = ANY (v_types))
                ORDER BY random()
                LIMIT GREATEST(0, (4 + floor(random() * 7)::int) - cardinality(v_types))
            ) q;
            v_types := v_types || COALESCE(v_extra, ARRAY[]::text[]);
            IF cardinality(v_types) = 0 THEN v_types := ARRAY['WEDDING']; END IF;
            INSERT INTO vendor_catered_event_types (vendor_profile_id, event_type)
            SELECT v_vp_id, x FROM unnest(v_types) AS x;

            FOR v_pkg IN SELECT value FROM jsonb_array_elements(v_cfg -> 'packages') LOOP
                v_pt := v_pkg ->> 'pt';
                v_lo := (v_pkg ->> 'lo')::numeric;
                v_hi := (v_pkg ->> 'hi')::numeric;
                v_price := NULL; v_min := NULL; v_max := NULL;
                IF v_pt = 'FIXED' THEN
                    v_price := round((v_lo + floor(random() * (v_hi - v_lo + 1))) / 500.0) * 500;
                ELSIF v_pt = 'RANGE' THEN
                    v_min := v_lo; v_max := v_hi;
                END IF;
                INSERT INTO vendor_packages (vendor_profile_id, name, description, price, package_type, active,
                        pricing_type, min_price, max_price, created_at, updated_at)
                VALUES (v_vp_id, v_pkg ->> 'n', v_pkg ->> 'd', v_price, v_ptype, true, v_pt, v_min, v_max, now(), now());
            END LOOP;
        END LOOP;

        RAISE NOTICE '  % : % vendors', rpad(v_type, 36), v_this;
    END LOOP;

    RAISE NOTICE '----------------------------------------------------------------';
    RAISE NOTICE 'Seeded % vendors, % packages, % subscriptions.',
        (SELECT count(*) FROM users WHERE google_id LIKE 'seedv-%'),
        (SELECT count(*) FROM vendor_packages p JOIN vendor_profiles vp ON vp.id = p.vendor_profile_id
            JOIN users u ON u.id = vp.user_id WHERE u.google_id LIKE 'seedv-%'),
        (SELECT count(*) FROM vendor_subscriptions s JOIN users u ON u.id = s.user_id WHERE u.google_id LIKE 'seedv-%');
    RAISE NOTICE 'Verified: %, Top Vendor: %',
        (SELECT count(*) FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id WHERE u.google_id LIKE 'seedv-%' AND vp.verified),
        (SELECT count(*) FROM vendor_profiles vp JOIN users u ON u.id = vp.user_id WHERE u.google_id LIKE 'seedv-%' AND vp.top_vendor);
END
$DO$;
