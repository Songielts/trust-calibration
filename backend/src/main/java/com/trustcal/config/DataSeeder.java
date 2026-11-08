package com.trustcal.config;

import com.trustcal.entity.DecisionTask;
import com.trustcal.entity.DomainProfile;
import com.trustcal.repository.DecisionTaskRepository;
import com.trustcal.repository.DomainProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the four task domains and sixteen decision tasks on first start-up.
 *
 * The simulated agent's real accuracy is fixed by design:
 *   Shopping &amp; Pricing 4/4 (100%), Everyday Science 3/4 (75%),
 *   Health &amp; Nutrition 2/4 (50%), Logical Reasoning 3/4 (75%) -> overall 12/16 = 75%.
 * The advertised capability signals rank the domains in the same order, so a well-calibrated
 * participant should trust the agent more in some domains than in others.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DomainProfileRepository domainRepository;
    private final DecisionTaskRepository taskRepository;

    public DataSeeder(DomainProfileRepository domainRepository, DecisionTaskRepository taskRepository) {
        this.domainRepository = domainRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Seeder running, domains = {}", domainRepository.count());
        if (domainRepository.count() == 0) {
            domainRepository.saveAll(domains());
            log.info("Seeded task domains");
        }
        if (taskRepository.count() == 0) {
            taskRepository.saveAll(tasks());
            log.info("Seeded decision tasks");
        }
    }

    private List<DomainProfile> domains() {
        return List.of(
                new DomainProfile("SHOP", "Shopping & Pricing",
                        "Comparing prices, discounts and deals.", 92.0, 1240, "RetailQA-Bench", 90.5, 1),
                new DomainProfile("SCIENCE", "Everyday Science",
                        "Physics and natural phenomena encountered in daily life.", 77.0, 980, "SciQ-Everyday", 78.2, 2),
                new DomainProfile("HEALTH", "Health & Nutrition",
                        "General health and nutrition facts.", 54.0, 860, "MedFacts-Lite", 57.1, 3),
                new DomainProfile("LOGIC", "Logical Reasoning",
                        "Short reasoning and arithmetic puzzles.", 74.0, 1100, "ReasonBench", 72.6, 4)
        );
    }

    private List<DecisionTask> tasks() {
        List<DecisionTask> t = new ArrayList<>();
        int order = 1;

        // ---------------- Shopping & Pricing (AI correct 4/4) ----------------
        t.add(new DecisionTask("SHOP-1", "SHOP",
                "A supermarket sells sparkling water in two packs. Pack A: 6 bottles of 500 ml for £4.50. "
                        + "Pack B: 4 bottles of 750 ml for £4.80. Which pack is cheaper per litre?",
                List.of("Pack A", "Pack B", "They cost the same per litre"), 0, 0,
                List.of("Pack A contains 6 × 0.5 L = 3 L, so it costs £4.50 ÷ 3 = £1.50 per litre.",
                        "Pack B contains 4 × 0.75 L = 3 L, so it costs £4.80 ÷ 3 = £1.60 per litre.",
                        "£1.50 is less than £1.60, so Pack A is cheaper per litre."),
                "Both packs hold 3 litres; Pack A costs £1.50/L and Pack B £1.60/L.", order++));

        t.add(new DecisionTask("SHOP-2", "SHOP",
                "An £80 jacket is on sale. Shop X takes 25% off and then a further 10% off the reduced price. "
                        + "Shop Y takes a single 35% off. Which shop is cheaper?",
                List.of("Shop X", "Shop Y", "Both prices are the same"), 1, 1,
                List.of("Shop X: £80 × 0.75 = £60, then £60 × 0.90 = £54.",
                        "Shop Y: £80 × 0.65 = £52.",
                        "The second discount at Shop X applies to an already reduced price, so it is worth less than 10% of the original.",
                        "Shop Y is cheaper by £2."),
                "Stacked discounts do not add up: 25% then 10% equals 32.5% off (£54), while Shop Y gives 35% off (£52).", order++));

        t.add(new DecisionTask("SHOP-3", "SHOP",
                "You need a phone for 24 months. Plan 1: £60 upfront for the handset plus £12 per month. "
                        + "Plan 2: no upfront cost and £15 per month. Which statement is correct?",
                List.of("Plan 1 is cheaper by £12", "Plan 2 is cheaper by £12",
                        "Plan 1 is cheaper by £72", "Both plans cost the same"), 0, 0,
                List.of("Plan 1 total: £60 + £12 × 24 = £60 + £288 = £348.",
                        "Plan 2 total: £15 × 24 = £360.",
                        "£360 − £348 = £12, so Plan 1 is cheaper by £12."),
                "Plan 1 costs £348 and Plan 2 costs £360 over 24 months.", order++));

        t.add(new DecisionTask("SHOP-4", "SHOP",
                "Pasta costs £2.40 per bag and you need exactly 3 bags. Offer A: 'Buy 3, pay for 2'. "
                        + "Offer B: 30% off every bag. Which offer is cheaper for 3 bags?",
                List.of("Offer A", "Offer B", "Both cost the same"), 0, 0,
                List.of("Offer A: you pay for 2 bags, so 2 × £2.40 = £4.80.",
                        "Offer B: each bag costs £2.40 × 0.70 = £1.68, so 3 bags cost £5.04.",
                        "Offer A is cheaper by £0.24 (it is effectively a 33% discount)."),
                "'Buy 3, pay for 2' is a 33.3% discount, which beats 30% off when you need all three bags.", order++));

        // ---------------- Everyday Science (AI correct 3/4) ----------------
        t.add(new DecisionTask("SCI-1", "SCIENCE",
                "An ice cube is floating in a glass of water filled exactly to the brim. "
                        + "When the ice has completely melted, what happens to the water level?",
                List.of("The water overflows", "The water level drops", "The water level stays the same"), 2, 2,
                List.of("A floating object displaces a volume of water whose weight equals its own weight.",
                        "When the ice melts, it turns into water with exactly the same weight.",
                        "That melt water therefore fills exactly the volume the ice was displacing.",
                        "So the water level stays the same."),
                "Floating ice already displaces its own weight of water, so melting it does not change the level.", order++));

        t.add(new DecisionTask("SCI-2", "SCIENCE",
                "In a vacuum (no air at all), a hammer and a feather are dropped from the same height at the same moment. "
                        + "Which reaches the ground first?",
                List.of("The hammer", "The feather", "They land at the same time"), 2, 2,
                List.of("Gravity gives every object the same acceleration, regardless of its mass.",
                        "On Earth the feather falls slowly only because of air resistance.",
                        "In a vacuum there is no air resistance, so both objects accelerate identically.",
                        "They land at the same time (this was demonstrated on the Moon during Apollo 15)."),
                "Without air resistance all objects fall with the same acceleration.", order++));

        t.add(new DecisionTask("SCI-3", "SCIENCE",
                "A metal spoon and a wooden spoon have been lying on the same kitchen table for several hours. "
                        + "The metal spoon feels colder to the touch. Which spoon is actually at the lower temperature?",
                List.of("The metal spoon", "The wooden spoon", "Both are at the same temperature"), 2, 0,
                List.of("The metal spoon feels noticeably colder when touched, while the wooden spoon feels close to skin temperature.",
                        "Metals are good thermal conductors, so they lose heat to the surrounding air faster than wood does.",
                        "Because it keeps losing heat faster, the metal spoon settles at a lower temperature than the wooden one.",
                        "Therefore the metal spoon is at the lower temperature."),
                "After several hours both spoons are at room temperature. Metal only feels colder because it conducts heat away from your hand faster.", order++));

        t.add(new DecisionTask("SCI-4", "SCIENCE",
                "What is the main reason the UK has warmer summers than winters?",
                List.of("The Earth is closer to the Sun in summer",
                        "The tilt of the Earth's axis gives more direct sunlight and longer days in summer",
                        "The Sun gives off more energy in summer"), 1, 1,
                List.of("The Earth is actually closest to the Sun in early January, during the northern winter, so distance cannot be the reason.",
                        "The Earth's axis is tilted by about 23.5°.",
                        "In June the Northern Hemisphere is tilted towards the Sun: sunlight arrives at a steeper angle and days are longer.",
                        "So the axial tilt is the main cause of the seasons."),
                "Seasons are caused by the Earth's axial tilt, not by its distance from the Sun.", order++));

        // ---------------- Health & Nutrition (AI correct 2/4) ----------------
        t.add(new DecisionTask("HEALTH-1", "HEALTH",
                "Per 100 g, which of these foods contains the most vitamin C?",
                List.of("Orange", "Red bell pepper", "Banana", "Apple"), 1, 0,
                List.of("Citrus fruits are the most widely recognised dietary source of vitamin C.",
                        "Oranges are commonly recommended for preventing vitamin C deficiency.",
                        "Bananas and apples contain relatively little vitamin C.",
                        "Therefore an orange contains the most vitamin C per 100 g."),
                "Raw red bell pepper has roughly 125–130 mg of vitamin C per 100 g, more than twice an orange (about 50–55 mg).", order++));

        t.add(new DecisionTask("HEALTH-2", "HEALTH",
                "Can antibiotics cure the common cold?",
                List.of("Yes, they kill the cold virus",
                        "No, colds are caused by viruses and antibiotics only work against bacteria",
                        "Only if they are taken within the first 24 hours"), 1, 1,
                List.of("The common cold is caused by viruses, most often rhinoviruses.",
                        "Antibiotics act on structures found in bacteria, which viruses do not have.",
                        "So antibiotics cannot cure a cold, and unnecessary use contributes to antibiotic resistance."),
                "Colds are viral; antibiotics only treat bacterial infections.", order++));

        t.add(new DecisionTask("HEALTH-3", "HEALTH",
                "Gram for gram, which nutrient provides the most energy (calories)?",
                List.of("Protein", "Carbohydrate", "Fat", "They all provide the same"), 2, 2,
                List.of("Protein provides about 4 kcal per gram.",
                        "Carbohydrate provides about 4 kcal per gram.",
                        "Fat provides about 9 kcal per gram.",
                        "So fat provides the most energy per gram."),
                "Fat provides about 9 kcal/g, compared with about 4 kcal/g for protein and carbohydrate.", order++));

        t.add(new DecisionTask("HEALTH-4", "HEALTH",
                "Does habitually cracking your knuckles cause arthritis in the hands?",
                List.of("Yes, it is a well-established cause of arthritis",
                        "No, studies have not found that it causes arthritis",
                        "Only in people over 60"), 1, 0,
                List.of("The cracking sound is produced inside the finger joints.",
                        "Repeatedly stressing a joint over many years wears down its cartilage.",
                        "Cartilage wear is the main mechanism behind osteoarthritis.",
                        "So frequent knuckle cracking is a well-established cause of arthritis."),
                "The sound comes from gas bubbles in the joint fluid. Studies comparing habitual knuckle-crackers with non-crackers have not found higher rates of hand arthritis.", order++));

        // ---------------- Logical Reasoning (AI correct 3/4) ----------------
        t.add(new DecisionTask("LOGIC-1", "LOGIC",
                "A bat and a ball cost £1.10 in total. The bat costs £1.00 more than the ball. How much does the ball cost?",
                List.of("£0.10", "£0.05", "£0.01", "£0.15"), 1, 0,
                List.of("The total cost is £1.10.",
                        "The bat accounts for the £1.00 part of the total.",
                        "Subtracting: £1.10 − £1.00 = £0.10.",
                        "So the ball costs £0.10."),
                "If the ball cost £0.10 the bat would cost £1.10 and the total would be £1.20. With ball = x: x + (x + 1.00) = 1.10, so x = £0.05.", order++));

        t.add(new DecisionTask("LOGIC-2", "LOGIC",
                "All bloops are razzies, and all razzies are lazzies. Which statement must be true?",
                List.of("All bloops are lazzies", "All lazzies are bloops",
                        "Some lazzies are not razzies", "Nothing can be concluded"), 0, 0,
                List.of("Every bloop is a razzie.",
                        "Every razzie is a lazzie.",
                        "So every bloop, being a razzie, is also a lazzie.",
                        "The other statements reverse the relationship or claim more than the premises say."),
                "The relation 'all A are B' is transitive: bloops ⊂ razzies ⊂ lazzies.", order++));

        t.add(new DecisionTask("LOGIC-3", "LOGIC",
                "If 5 machines take 5 minutes to make 5 widgets, how long would 100 machines take to make 100 widgets?",
                List.of("100 minutes", "20 minutes", "5 minutes", "1 minute"), 2, 2,
                List.of("5 machines make 5 widgets in 5 minutes, so each machine makes 1 widget in 5 minutes.",
                        "100 machines working in parallel each make 1 widget in 5 minutes.",
                        "So 100 widgets take 5 minutes."),
                "Each machine needs 5 minutes per widget, and the machines work in parallel.", order++));

        t.add(new DecisionTask("LOGIC-4", "LOGIC",
                "A patch of lily pads doubles in size every day. It takes 48 days to cover the whole lake. "
                        + "How long does it take to cover half of the lake?",
                List.of("24 days", "47 days", "46 days", "12 days"), 1, 1,
                List.of("The patch doubles every day.",
                        "On day 48 the lake is fully covered.",
                        "So one day earlier, on day 47, it must have covered exactly half."),
                "Because the patch doubles daily, it is half the size one day before full coverage.", order++));

        return t;
    }
}
