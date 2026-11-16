package com.ardrawing.sketch

object AnimeCatalog {

    data class Series(val name: String, val characters: List<String>)

    private fun s(name: String, csv: String) =
        Series(name, csv.split(",").map { it.trim() }.filter { it.isNotEmpty() })

    val list = listOf(
        s("Naruto", "Naruto Uzumaki,Sasuke Uchiha,Sakura Haruno,Kakashi Hatake,Itachi Uchiha,Hinata Hyuga,Minato Namikaze,Jiraiya,Tsunade,Orochimaru,Gaara,Rock Lee,Neji Hyuga,Shikamaru Nara,Madara Uchiha,Obito Uchiha,Pain Nagato,Konan,Kurama Nine Tails,Hashirama Senju,Tobirama Senju,Killer Bee,Might Guy,Boruto Uzumaki,Sarada Uchiha"),
        s("Demon Slayer", "Tanjiro Kamado,Nezuko Kamado,Zenitsu Agatsuma,Inosuke Hashibira,Giyu Tomioka,Kyojuro Rengoku,Shinobu Kocho,Tengen Uzui,Mitsuri Kanroji,Muichiro Tokito,Obanai Iguro,Sanemi Shinazugawa,Gyomei Himejima,Kanao Tsuyuri,Genya Shinazugawa,Muzan Kibutsuji,Akaza,Doma,Kokushibo,Rui,Daki,Yoriichi Tsugikuni"),
        s("Jujutsu Kaisen", "Yuji Itadori,Megumi Fushiguro,Nobara Kugisaki,Satoru Gojo,Ryomen Sukuna,Yuta Okkotsu,Maki Zenin,Toge Inumaki,Panda,Kento Nanami,Suguru Geto,Toji Fushiguro,Choso,Todo Aoi,Mahito,Jogo,Kenjaku,Mei Mei,Utahime Iori,Yuki Tsukumo,Kinji Hakari,Kashimo Hajime"),
        s("One Piece", "Monkey D Luffy,Roronoa Zoro,Nami,Usopp,Vinsmoke Sanji,Tony Tony Chopper,Nico Robin,Franky,Brook,Jinbe,Portgas D Ace,Sabo,Shanks,Trafalgar Law,Eustass Kid,Boa Hancock,Dracule Mihawk,Kaido,Big Mom,Whitebeard,Gol D Roger,Yamato,Gear 5 Luffy,Doflamingo"),
        s("Dragon Ball", "Son Goku,Vegeta,Gohan,Piccolo,Future Trunks,Frieza,Cell,Majin Buu,Broly,Beerus,Whis,Krillin,Bulma,Android 18,Android 17,Master Roshi,Tien Shinhan,Bardock,Gogeta,Vegito,Goku Black,Jiren"),
        s("Attack on Titan", "Eren Yeager,Mikasa Ackerman,Armin Arlert,Levi Ackerman,Erwin Smith,Hange Zoe,Jean Kirstein,Sasha Braus,Connie Springer,Historia Reiss,Reiner Braun,Bertolt Hoover,Annie Leonhart,Zeke Yeager,Ymir,Falco Grice,Gabi Braun,Pieck Finger,Colossal Titan,Armored Titan"),
        s("My Hero Academia", "Izuku Midoriya Deku,Katsuki Bakugo,Shoto Todoroki,Ochaco Uraraka,Tenya Iida,All Might,Shota Aizawa,Tsuyu Asui,Eijiro Kirishima,Denki Kaminari,Momo Yaoyorozu,Fumikage Tokoyami,Hawks,Endeavor,Mirko,Tomura Shigaraki,Dabi,Himiko Toga,Twice,Overhaul,Eri,Stain"),
        s("Chainsaw Man", "Denji,Chainsaw Man Devil,Power,Aki Hayakawa,Makima,Pochita,Kobeni,Himeno,Kishibe,Reze,Quanxi,Angel Devil,Beam,Violence Fiend,Prinz,Hirofumi Yoshida,Asa Mitaka,Yoru War Devil,Gun Devil,Katana Man"),
        s("Solo Leveling", "Sung Jinwoo,Igris,Beru,Tank,Iron,Tusk,Kaisel,Bellion,Cha Haein,Yoo Jinho,Baek Yoonho,Choi Jongin,Go Gunhee,Thomas Andre,Liu Zhigang,Christopher Reed,Sung Ilhwan,Esil Radiru,Ant King,Statue of God"),
        s("Spy x Family", "Anya Forger,Loid Forger Twilight,Yor Forger,Bond Forger,Damian Desmond,Becky Blackbell,Yuri Briar,Sylvia Sherwood,Franky Franklin,Fiona Frost,Henry Henderson,Donovan Desmond,Bill Watkins,Camilla,Millie,Sharon,Emile Elman,Ewen Egeburg,Martha Marriott,Anya Smug Face"),
        s("Tokyo Revengers", "Takemichi Hanagaki,Manjiro Sano Mikey,Ken Ryuguji Draken,Keisuke Baji,Chifuyu Matsuno,Takashi Mitsuya,Kazutora Hanemiya,Hinata Tachibana,Naoto Tachibana,Tetta Kisaki,Shuji Hanma,Taiju Shiba,Hakkai Shiba,Izana Kurokawa,Kakucho,Senju Kawaragi,Sanzu Haruchiyo,Ran Haitani,Rindou Haitani,Smiley Nahoya"),
        s("Death Note", "Light Yagami,L Lawliet,Ryuk,Misa Amane,Near,Mello,Rem,Soichiro Yagami,Touta Matsuda,Shuichi Aizawa,Kanzo Mogi,Teru Mikami,Kiyomi Takada,Naomi Misora,Raye Penber,Watari,Matt Jeevas,Gelus,Sidoh,Kira Symbol"),
        s("Bleach", "Ichigo Kurosaki,Rukia Kuchiki,Orihime Inoue,Uryu Ishida,Yasutora Sado,Renji Abarai,Byakuya Kuchiki,Toshiro Hitsugaya,Kenpachi Zaraki,Kisuke Urahara,Yoruichi Shihoin,Sosuke Aizen,Ulquiorra Cifer,Grimmjow Jaegerjaquez,Gin Ichimaru,Rangiku Matsumoto,Shunsui Kyoraku,Yamamoto Genryusai,Unohana Retsu,Mayuri Kurotsuchi,Nelliel,Yhwach"),
        s("Hunter x Hunter", "Gon Freecss,Killua Zoldyck,Kurapika,Leorio Paradinight,Hisoka Morow,Chrollo Lucilfer,Illumi Zoldyck,Meruem,Neferpitou,Komugi,Isaac Netero,Biscuit Krueger,Kite,Feitan Portor,Machi Komacine,Shalnark,Uvogin,Zeno Zoldyck,Silva Zoldyck,Alluka Zoldyck"),
        s("Black Clover", "Asta,Yuno,Noelle Silva,Yami Sukehiro,Julius Novachrono,Mereoleona Vermillion,Fuegoleon Vermillion,Luck Voltia,Magna Swing,Vanessa Enoteca,Charmy Pappitson,Finral Roulacase,Gauche Adlai,Grey,Gordon Agrippa,Nacht Faust,Mimosa Vermillion,William Vangeance,Zenon Zogratis,Liebe"),
        s("Blue Lock", "Yoichi Isagi,Meguru Bachira,Rin Itoshi,Sae Itoshi,Seishiro Nagi,Reo Mikage,Shoei Barou,Hyoma Chigiri,Rensuke Kunigami,Julian Loki,Noel Noa,Jinpachi Ego,Ryusei Shidou,Tabito Karasu,Eita Otoya,Kenyu Yukimiya,Oliver Aiku,Michael Kaiser,Alexis Ness,Gin Gagamaru"),
        s("One Punch Man", "Saitama,Genos,Tatsumaki,Fubuki,King,Bang Silver Fang,Atomic Samurai,Child Emperor,Zombieman,Sweet Mask,Mumen Rider,Speed o Sound Sonic,Garou,Boros,Metal Bat,Flashy Flash,Watchdog Man,Drive Knight,Pig God,Puri Puri Prisoner"),
        s("Tokyo Ghoul", "Ken Kaneki,Touka Kirishima,Rize Kamishiro,Juzo Suzuya,Hideyoshi Nagachika,Kisho Arima,Koutarou Amon,Akira Mado,Hinami Fueguchi,Shu Tsukiyama,Nishiki Nishio,Renji Yomo,Uta,Yoshimura,Eto Yoshimura,Seidou Takizawa,Kuki Urie,Tooru Mutsuki,Saiko Yonebayashi,Haise Sasaki"),
        s("Fullmetal Alchemist", "Edward Elric,Alphonse Elric,Winry Rockbell,Roy Mustang,Riza Hawkeye,Scar,Maes Hughes,Alex Louis Armstrong,Olivier Armstrong,Ling Yao,Greed,Envy,Lust,Gluttony,King Bradley,Selim Bradley,Sloth,Van Hohenheim,Izumi Curtis,Mei Chang"),
        s("Haikyuu", "Shoyo Hinata,Tobio Kageyama,Daichi Sawamura,Koshi Sugawara,Asahi Azumane,Yu Nishinoya,Ryunosuke Tanaka,Kei Tsukishima,Tadashi Yamaguchi,Toru Oikawa,Hajime Iwaizumi,Kenma Kozume,Tetsuro Kuroo,Kotaro Bokuto,Keiji Akaashi,Wakatoshi Ushijima,Satori Tendo,Atsumu Miya,Osamu Miya,Shinsuke Kita")
    )

    private val aliases = mapOf(
        "নারুতো" to "naruto",
        "নারুটো" to "naruto",
        "সাসুকে" to "sasuke",
        "সাস্কে" to "sasuke",
        "সাসেক" to "sasuke",
        "সাকুরা" to "sakura",
        "কাকাশি" to "kakashi",
        "কাকাসি" to "kakashi",
        "ইতাচি" to "itachi",
        "হিনাতা" to "hinata",
        "হিনাটা" to "hinata",
        "হিনটা" to "hinata",
        "মিনাতো" to "minato",
        "জিরাইয়া" to "jiraiya",
        "জিরাইয়া" to "jiraiya",
        "মাস্টার জিরাইয়া" to "jiraiya",
        "মাস্টার জিরাইয়া" to "jiraiya",
        "master jiraiya" to "jiraiya",
        "সুনাদে" to "tsunade",
        "ওরোচিমারু" to "orochimaru",
        "গারা" to "gaara",
        "রক লি" to "rock lee",
        "নেজি" to "neji",
        "শিকামারু" to "shikamaru",
        "মাদারা" to "madara",
        "ওবিতো" to "obito",
        "পেইন" to "pain",
        "কুরামা" to "kurama",
        "বোরুতো" to "boruto",
        "তানজিরো" to "tanjiro",
        "নেজুকো" to "nezuko",
        "জেনিৎসু" to "zenitsu",
        "ইনোসুকে" to "inosuke",
        "গিউ" to "giyu",
        "রেঙ্গোকু" to "rengoku",
        "শিনোবু" to "shinobu",
        "মুজান" to "muzan",
        "ইউজু" to "yuji",
        "মেগুমি" to "megumi",
        "গোজো" to "gojo",
        "সুকুনা" to "sukuna",
        "লুফি" to "luffy",
        "জোরো" to "zoro",
        "নামি" to "nami",
        "সানজি" to "sanji",
        "শ্যাঙ্কস" to "shanks",
        "এস" to "ace",
        "গোকু" to "goku",
        "ভেজিটা" to "vegeta",
        "গোহান" to "gohan",
        "পিকোলো" to "piccolo",
        "ফ্রিজা" to "frieza",
        "ব্রলি" to "broly",
        "এরেন" to "eren",
        "মিকাসা" to "mikasa",
        "লেভি" to "levi",
        "দেকু" to "deku",
        "বাকুগো" to "bakugo",
        "টোডোরোকি" to "todoroki",
        "অল মাইট" to "all might",
        "ডেনজি" to "denji",
        "পাওয়ার" to "power",
        "মাকিমা" to "makima",
        "জিনউ" to "jinwoo",
        "আনিয়া" to "anya",
        "মাইকি" to "mikey",
        "লাইট" to "light",
        "ইচিগো" to "ichigo",
        "রুকিয়া" to "rukia",
        "গন" to "gon",
        "কিলুয়া" to "killua",
        "আস্তা" to "asta",
        "ইসাগি" to "isagi",
        "সাইতামা" to "saitama",
        "জেনোস" to "genos",
        "কানেকি" to "kaneki",
        "এডওয়ার্ড" to "edward"
    )

    fun normalizeQuery(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val low = t.lowercase()
        aliases[low]?.let { return it }
        aliases.forEach { (k, v) ->
            if (low.contains(k)) return low.replace(k, v)
        }
        return t
    }

    fun searchAll(query: String): List<Pair<String, String>> {
        val q = normalizeQuery(query).trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<Pair<String, String>>()
        val contains = ArrayList<Pair<String, String>>()
        val seriesMatches = ArrayList<Pair<String, String>>()
        list.forEach { series ->
            val seriesHit = series.name.lowercase().contains(q)
            series.characters.forEach { ch ->
                val low = ch.lowercase()
                if (low.startsWith(q)) starts.add(ch to series.name)
                else if (low.contains(q)) contains.add(ch to series.name)
                else if (seriesHit) seriesMatches.add(ch to series.name)
            }
        }
        return (starts + contains + seriesMatches).distinctBy { it.first }.take(30)
    }
}
