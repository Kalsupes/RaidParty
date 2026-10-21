package com.github.kalsupes.raidparty.defence;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/** Bundled NPC-id data; no network request is made by this feature. */
public final class DefenceProfile
{
    private static final Map<Integer, List<DefenceProfile>> BY_ID = new HashMap<>();
    private static final Map<String, List<DefenceProfile>> BY_NAME = new HashMap<>();
    private static final Pattern ROW = Pattern.compile(
        "^\\s*\\{\"id\":(\\d+),\"name\":\"((?:\\\\.|[^\"\\\\])*)\",\"variant\":\"((?:\\\\.|[^\"\\\\])*)\","
        + "\"defence\":(\\d+),\"magic\":(\\d+),\"attack\":(\\d+),\"strength\":(\\d+),\"ranged\":(\\d+),"
        + "\"magicDefence\":(-?\\d+),\"demon\":(true|false),\"cox\":(true|false)\\},?$");
    static
    {
        try (InputStream stream = DefenceProfile.class.getResourceAsStream("/defence/npcs.json"))
        {
            if (stream == null)
            {
                throw new IllegalStateException("Missing bundled defence profiles");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null)
            {
                Matcher m = ROW.matcher(line);
                if (!m.matches()) continue;
                DefenceProfile profile = new DefenceProfile();
                profile.id = Integer.parseInt(m.group(1));
                profile.name = unescape(m.group(2));
                profile.variant = unescape(m.group(3));
                profile.defence = Integer.parseInt(m.group(4));
                profile.magic = Integer.parseInt(m.group(5));
                profile.attack = Integer.parseInt(m.group(6));
                profile.strength = Integer.parseInt(m.group(7));
                profile.ranged = Integer.parseInt(m.group(8));
                profile.magicDefence = Integer.parseInt(m.group(9));
                profile.demon = Boolean.parseBoolean(m.group(10));
                profile.cox = Boolean.parseBoolean(m.group(11));
                BY_ID.computeIfAbsent(profile.id, key -> new ArrayList<>()).add(profile);
                BY_NAME.computeIfAbsent(normalize(profile.name), key -> new ArrayList<>()).add(profile);
            }
            aliases(11278, 11279, 11280, 11281, 11282); // Nex phases
            aliases(7540, 7541, 7542, 7544, 7545); // Tekton / CM
            aliases(7550, 7553); aliases(7551, 7554); aliases(7552, 7555);
            aliases(7584, 7585); aliases(7569, 7570, 7571, 7572);
            aliases(7530, 7531, 7532); aliases(7538, 7539); aliases(7548, 7549);
            aliases(7573, 7574, 7575, 7576); aliases(7604, 7605, 7606);
            aliases(8387, 8388); aliases(10864, 10865); aliases(10867, 10868);
            aliases(8340, 8338, 8339); aliases(10768, 10767); aliases(10772, 10770, 10771);
            aliases(8354, 8355, 8356, 8357); aliases(10786, 10787, 10788, 10789);
            aliases(10807, 10808, 10809, 10810);
            aliases(8360, 8364, 8365); aliases(10814, 10815, 10816, 10817, 10818, 10819);
            aliases(10822, 10823, 10824, 10825, 10826, 10827);
            aliases(8369, 8370, 8371); aliases(8372, 8373);
            aliases(10830, 10831, 10832); aliases(10833, 10834);
            aliases(10847, 10848, 10849); aliases(10850, 10851);
            aliases(11789, 11790, 11791, 11792, 11793, 11794, 11795, 11796);
            aliases(11797, 11798, 11799); aliases(11778, 11779, 11780);
            aliases(11730, 11733); aliases(11753, 11754); aliases(11756, 11757);
            aliases(9425, 378, 9426, 9427, 9428, 9429, 9430, 9431, 9432, 9433, 9460);
            aliases(9416, 377, 9417, 9418, 9419, 9420, 9421, 9422, 9423, 9424, 11153, 11154, 11155);
        }
        catch (java.io.IOException ex)
        {
            throw new ExceptionInInitializerError(ex);
        }
    }

    int id;
    String name;
    String variant;
    int defence;
    int magic;
    int attack;
    int strength;
    int ranged;
    int magicDefence;
    boolean demon;
    boolean cox;

    private static void aliases(int canonical, int... ids)
    {
        List<DefenceProfile> profiles = BY_ID.get(canonical);
        if (profiles != null) for (int id : ids) BY_ID.putIfAbsent(id, profiles);
    }

    private static String unescape(String s)
    {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length())
            {
                char n = s.charAt(++i);
                if (n == 'u' && i + 4 < s.length())
                {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                else out.append(n);
            }
            else out.append(c);
        }
        return out.toString();
    }

    public static String normalize(String name)
    {
        return name == null ? "" : Text.removeTags(name).trim().toLowerCase(Locale.ROOT);
    }

    public static DefenceProfile find(int id, String name, boolean awakened, boolean enraged)
    {
        List<DefenceProfile> candidates = BY_ID.get(id);
        if (candidates == null)
        {
            // Name fallback is allowed only when all matching entries have identical stats.
            // This covers animation-only NPC transformations without conflating boss modes.
            candidates = BY_NAME.get(normalize(name));
            if (candidates == null && normalize(name).equals("tekton (enraged)")) candidates = BY_ID.get(7543);
        }
        if (candidates == null)
        {
            return null;
        }
        DefenceProfile result = null;
        for (DefenceProfile p : candidates)
        {
            String v = p.variant == null ? "" : p.variant.toLowerCase(Locale.ROOT);
            if (v.contains("awakened") != awakened && (v.contains("awakened") || v.contains("post-quest")))
            {
                continue;
            }
            if (normalize(p.name).contains("warden") && v.equals("enraged") != enraged
                && (v.equals("enraged") || v.equals("damaged")))
            {
                continue;
            }
            if (result != null && !result.sameStats(p))
            {
                return null;
            }
            result = p;
        }
        return result;
    }

    private boolean sameStats(DefenceProfile other)
    {
        return defence == other.defence && magic == other.magic && attack == other.attack
            && strength == other.strength && ranged == other.ranged && demon == other.demon
            && cox == other.cox;
    }

    boolean stableMagicDefence()
    {
        List<DefenceProfile> variants = BY_ID.get(id);
        return variants == null || variants.stream().noneMatch(p -> sameStats(p) && magicDefence != p.magicDefence);
    }

    public int floor()
    {
        if (variant != null && variant.toLowerCase(Locale.ROOT).contains("awakened")) return defence;
        switch (normalize(name))
        {
            case "nex": return 250;
            case "yama": return 145;
            case "sotetseg": return 100;
            case "the nightmare":
            case "phosani's nightmare":
            case "the hueycoatl": return 120;
            case "araxxor": return 90;
            case "akkha": return 70;
            case "ba-ba":
            case "kephri":
            case "obelisk": return 60;
            case "zebak": return 50;
            case "elidinis' warden":
            case "tumeken's warden":
                return Arrays.asList(11761, 11762, 11763, 11764).contains(id) ? 120 : 0;
            case "verzik vitur": return defence;
            default: return 0;
        }
    }

    public boolean dynamicDefence()
    {
        // Vardorvis' level changes with exact HP, which is not exposed to the client.
        return normalize(name).equals("vardorvis");
    }

    /** Only reviewed boss families may advertise a reached floor. Unknowns remain neutral. */
    public boolean verifiedFloor()
    {
        return Arrays.asList("nex", "yama", "sotetseg", "the nightmare", "phosani's nightmare",
            "the hueycoatl", "araxxor", "akkha", "ba-ba", "kephri", "obelisk", "elidinis' warden",
            "tumeken's warden", "duke sucellus", "the whisperer", "the leviathan", "verzik vitur",
            "corporeal beast", "pestilent bloat", "tekton", "tekton (enraged)", "great olm",
            "great olm (left claw)", "great olm (right claw)", "the maiden of sugadinti", "xarpus",
            "nylocas vasilias", "general graardor", "commander zilyana", "kree'arra", "k'ril tsutsaroth",
            "vorkath", "zulrah", "cerberus", "abyssal sire", "alchemical hydra", "giant mole",
            "king black dragon", "dagannoth prime", "dagannoth rex", "dagannoth supreme",
            "kalphite queen", "sarachnis", "skotizo", "chaos elemental", "scorpia", "callisto",
            "artio", "venenatis", "spindel", "vet'ion", "calvar'ion").contains(normalize(name));
    }

    public boolean fastRecovery()
    {
        return Arrays.asList("akkha", "the nightmare", "phosani's nightmare").contains(normalize(name));
    }

    public String getName() { return name; }
    public int getDefence() { return defence; }
    public int getMagic() { return magic; }
    public boolean isCox() { return cox; }
}
