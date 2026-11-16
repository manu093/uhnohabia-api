package com.sharedshoppinglists.app.domain.classifier

import java.text.Normalizer

/**
 * Clasificador offline de productos de supermercado (gratis, sin red).
 *
 * - [parseLine] limpia una linea pegada (viñetas de WhatsApp, checks, numeracion) y
 *   separa cantidad y unidad: "- 2 kg de papa" -> (papa, 2.0, kg).
 * - [classify] sugiere categoria (de las categorias por defecto de la app) y emoji
 *   a partir de un diccionario de palabras clave.
 */
object ProductClassifier {

    data class ParsedItem(val name: String, val quantity: Double, val unit: String)
    data class Suggestion(val categoryName: String, val emoji: String)

    // Nombres de las categorias por defecto (AppDatabase.ensureDefaultCategories)
    const val CARNICERIA = "Carnicería"
    const val VERDULERIA = "Verdulería"
    const val LIMPIEZA = "Limpieza"
    const val PERFUMERIA = "Perfumería"
    const val GRANJA = "Granja"
    const val BEBIDAS = "Bebidas"
    const val ALMACEN = "Almacén"
    const val OTROS = "Otros"

    private class Entry(val tokens: List<String>, val category: String, val emoji: String)

    /** palabra clave (sin acentos, singular) -> categoria + emoji. Las frases ganan a palabras sueltas. */
    private val DICTIONARY: List<Entry> = buildList {
        fun add(category: String, emoji: String, vararg keywords: String) {
            keywords.forEach { add(Entry(normalize(it).split(' '), category, emoji)) }
        }

        // --- Verdulería
        add(VERDULERIA, "🍎", "manzana")
        add(VERDULERIA, "🍌", "banana", "banano")
        add(VERDULERIA, "🍊", "naranja", "mandarina", "pomelo")
        add(VERDULERIA, "🍋", "limon", "lima")
        add(VERDULERIA, "🍐", "pera")
        add(VERDULERIA, "🍑", "durazno", "pelon", "damasco", "ciruela")
        add(VERDULERIA, "🍇", "uva")
        add(VERDULERIA, "🍓", "frutilla", "arandano", "frambuesa")
        add(VERDULERIA, "🥝", "kiwi")
        add(VERDULERIA, "🍍", "anana")
        add(VERDULERIA, "🍉", "sandia")
        add(VERDULERIA, "🍈", "melon")
        add(VERDULERIA, "🥭", "mango")
        add(VERDULERIA, "🥑", "palta")
        add(VERDULERIA, "🍅", "tomate", "tomate cherry")
        add(VERDULERIA, "🥬", "lechuga", "acelga", "espinaca", "rucula", "repollo", "radicheta", "kale", "verdura")
        add(VERDULERIA, "🧅", "cebolla", "cebolla de verdeo", "verdeo", "puerro")
        add(VERDULERIA, "🥔", "papa", "batata")
        add(VERDULERIA, "🥕", "zanahoria", "remolacha", "rabanito")
        add(VERDULERIA, "🎃", "zapallo", "calabaza")
        add(VERDULERIA, "🥒", "zapallito", "pepino", "zucchini")
        add(VERDULERIA, "🍆", "berenjena")
        add(VERDULERIA, "🫑", "morron", "pimiento", "aji")
        add(VERDULERIA, "🥦", "brocoli", "coliflor")
        add(VERDULERIA, "🌽", "choclo", "maiz")
        add(VERDULERIA, "🧄", "ajo")
        add(VERDULERIA, "🌿", "perejil", "albahaca", "cilantro", "menta", "romero", "apio")
        add(VERDULERIA, "🍄", "champignon", "hongo")
        add(VERDULERIA, "🫛", "chaucha", "arveja fresca")
        add(VERDULERIA, "🍏", "fruta")

        // --- Carnicería
        add(CARNICERIA, "🥩", "carne", "carne picada", "picada", "nalga", "bife", "bife de chorizo", "asado",
            "vacio", "matambre", "peceto", "cuadril", "colita de cuadril", "lomo", "roast beef", "tapa de asado",
            "osobuco", "falda", "entraña", "churrasco", "bola de lomo", "paleta", "carne para guiso")
        add(CARNICERIA, "🍗", "pollo", "pechuga", "pata muslo", "muslo", "suprema", "alita", "pollo entero")
        add(CARNICERIA, "🐷", "cerdo", "bondiola", "carre", "pechito", "matambrito")
        add(CARNICERIA, "🌭", "chorizo", "morcilla", "salchicha", "salchicha parrillera", "achura", "chinchulin", "molleja")
        add(CARNICERIA, "🥩", "milanesa", "hamburguesa", "medallon")
        add(CARNICERIA, "🐟", "pescado", "merluza", "salmon", "filet", "atun fresco")
        add(CARNICERIA, "🦐", "langostino", "camaron", "mariscos")
        add(CARNICERIA, "🥓", "panceta")

        // --- Granja (huevos y lácteos)
        add(GRANJA, "🥚", "huevo", "maple de huevos")
        add(GRANJA, "🥛", "leche", "leche descremada", "leche entera", "leche en polvo", "leche chocolatada")
        add(GRANJA, "🧀", "queso", "queso rallado", "queso cremoso", "queso crema", "queso untable", "mozzarella",
            "muzzarella", "muzza", "ricota", "ricotta", "reggianito", "sardo", "provoleta", "port salut", "cheddar")
        add(GRANJA, "🧈", "manteca", "margarina")
        add(GRANJA, "🥛", "yogur", "yogurt", "postre lacteo", "flan")
        add(GRANJA, "🥛", "crema", "crema de leche")
        add(GRANJA, "🍖", "jamon", "jamon cocido", "jamon crudo", "fiambre", "salame", "mortadela", "paleta cocida",
            "lomito", "salchichon", "queso y jamon")

        // --- Almacén
        add(ALMACEN, "🍞", "pan", "pan lactal", "pan integral", "pan de molde", "pan rallado", "pan hamburguesa",
            "pan pancho", "baguette", "felipe", "flauta", "tostada", "grisin", "prepizza", "pizza", "rebozador")
        add(ALMACEN, "🥐", "medialuna", "factura", "bizcocho", "budin", "bizcochuelo", "torta")
        add(ALMACEN, "🍝", "fideo", "fideos", "spaghetti", "tallarin", "mostachol", "tirabuzon", "ñoqui", "raviol",
            "sorrentino", "canelon", "lasagna", "pasta")
        add(ALMACEN, "🍚", "arroz", "polenta", "harina", "harina leudante", "maicena", "premezcla", "avena", "quinoa", "semola")
        add(ALMACEN, "🫘", "lenteja", "poroto", "garbanzo", "arveja", "legumbre")
        add(ALMACEN, "🫒", "aceite", "aceite de oliva", "aceite de girasol", "oliva", "aceituna")
        add(ALMACEN, "🧂", "sal", "sal gruesa", "sal fina", "pimienta", "oregano", "pimenton", "comino", "condimento",
            "provenzal", "nuez moscada", "laurel", "aji molido", "curcuma", "caldo", "caldo en cubo", "sazonador")
        add(ALMACEN, "🍬", "azucar", "edulcorante", "stevia")
        add(ALMACEN, "🧉", "yerba", "yerba mate", "mate cocido")
        add(ALMACEN, "☕", "cafe", "te", "te en saquito", "capuchino", "cacao", "nesquik", "chocolatada en polvo")
        add(ALMACEN, "🍪", "galletita", "galleta", "galletitas dulces", "galletitas saladas", "oblea", "alfajor",
            "vainilla", "cereal", "copos", "barrita", "granola")
        add(ALMACEN, "🍫", "chocolate", "bombon", "caramelo", "chicle", "golosina", "turron")
        add(ALMACEN, "🍯", "dulce de leche", "mermelada", "miel", "dulce de membrillo", "dulce de batata", "nutella")
        add(ALMACEN, "🥫", "pure de tomate", "tomate triturado", "salsa de tomate", "salsa", "lata", "atun",
            "caballa", "sardina", "choclo en lata", "arvejas en lata", "duraznos en almibar", "conserva", "pickles")
        add(ALMACEN, "🥫", "mayonesa", "ketchup", "mostaza", "salsa golf", "salsa de soja", "vinagre", "aceto", "aderezo")
        add(ALMACEN, "🥟", "tapa de empanada", "tapas de empanadas", "tapa de tarta", "tapas para tarta", "pascualina", "empanada")
        add(ALMACEN, "🍿", "papas fritas", "snack", "palitos", "chizito", "mani", "pochoclo", "nachos")
        add(ALMACEN, "🥜", "frutos secos", "almendra", "nuez", "pasa de uva", "semilla", "coco rallado")
        add(ALMACEN, "🧊", "helado", "congelado", "papas congeladas", "vegetales congelados", "nugget", "hielo")
        add(ALMACEN, "🍮", "gelatina", "postre", "levadura", "polvo de hornear", "esencia de vainilla")

        // --- Bebidas
        add(BEBIDAS, "💧", "agua", "agua mineral", "agua con gas", "agua saborizada", "soda", "sifon")
        add(BEBIDAS, "🥤", "gaseosa", "coca", "coca cola", "sprite", "fanta", "pepsi", "seven up", "7up", "manaos",
            "paso de los toros", "tonica", "pomelo gaseosa", "energizante", "speed", "isotonica", "gatorade", "powerade")
        add(BEBIDAS, "🧃", "jugo", "jugo en polvo", "tang", "clight", "cepita", "baggio", "ades")
        add(BEBIDAS, "🍺", "cerveza", "birra", "quilmes", "brahma", "stella", "heineken", "andes", "corona", "patagonia")
        add(BEBIDAS, "🍷", "vino", "vino tinto", "vino blanco", "malbec", "espumante", "champagne", "sidra", "cidra")
        add(BEBIDAS, "🥃", "fernet", "aperitivo", "gancia", "campari", "whisky", "vodka", "ron", "gin", "licor")

        // --- Limpieza
        add(LIMPIEZA, "🧽", "detergente", "esponja", "virulana", "rejilla", "trapo", "trapo de piso", "secador",
            "escoba", "balde", "pala", "franela", "guante", "guantes de limpieza")
        add(LIMPIEZA, "🧴", "lavandina", "cloro", "desinfectante", "lysoform", "limpiador", "cif", "mr musculo",
            "limpiavidrio", "desengrasante", "antigrasa", "cera", "blem", "poett", "procenex", "quitamanchas", "vanish")
        add(LIMPIEZA, "🧺", "jabon en polvo", "jabon liquido para ropa", "jabon para ropa", "suavizante", "skip",
            "ariel", "ala", "comfort", "almidon", "apresto")
        add(LIMPIEZA, "🗑️", "bolsa de residuos", "bolsa de basura", "bolsas de consorcio", "bolsa", "residuo")
        add(LIMPIEZA, "🧻", "papel higienico", "rollo de cocina", "papel de cocina", "servilleta", "pañuelo descartable")
        add(LIMPIEZA, "🪳", "insecticida", "raid", "fuyi", "espiral", "repelente", "off")
        add(LIMPIEZA, "🌸", "desodorante de ambiente", "aromatizante", "glade")
        add(LIMPIEZA, "🍳", "papel aluminio", "film", "papel manteca", "bolsa freezer")

        // --- Perfumería
        add(PERFUMERIA, "🧴", "shampoo", "champu", "acondicionador", "crema de enjuague", "crema corporal",
            "crema de manos", "protector solar", "bronceador", "gel", "gel de ducha", "colonia", "perfume", "talco",
            "crema", "crema facial", "desmaquillante", "alcohol en gel", "alcohol")
        add(PERFUMERIA, "🧼", "jabon", "jabon de tocador", "jabon liquido")
        add(PERFUMERIA, "🦷", "pasta dental", "dentifrico", "crema dental", "cepillo de dientes", "cepillo dental",
            "hilo dental", "enjuague bucal", "colgate")
        add(PERFUMERIA, "🪒", "afeitadora", "maquinita", "espuma de afeitar", "gillette", "cera depilatoria")
        add(PERFUMERIA, "🧷", "desodorante", "antitranspirante", "rexona", "axe", "dove")
        add(PERFUMERIA, "👶", "pañal", "pañales", "toallita humeda", "toallitas humedas", "oleo calcareo")
        add(PERFUMERIA, "🩸", "toallita femenina", "toallitas femeninas", "tampon", "protector diario")
        add(PERFUMERIA, "🩹", "curita", "algodon", "hisopo", "agua oxigenada", "gasa", "ibuprofeno", "paracetamol",
            "aspirina", "remedio")

        // --- Otros
        add(OTROS, "🐶", "alimento para perro", "comida de perro", "alimento perro", "dog chow", "pedigree")
        add(OTROS, "🐱", "alimento para gato", "comida de gato", "alimento gato", "piedras sanitarias", "whiskas", "cat chow")
        add(OTROS, "🔋", "pila", "bateria")
        add(OTROS, "💡", "lamparita", "foco")
        add(OTROS, "🕯️", "vela", "fosforo", "encendedor")
        add(OTROS, "🔥", "carbon", "leña")
    }

    /** Palabras de unidad -> unidades de la app (AddEditProductDialog: Kg, Unidad, Gr, Docena, Litro). */
    private val UNIT_WORDS = mapOf(
        "kg" to "Kg", "kgs" to "Kg", "kilo" to "Kg", "kilos" to "Kg",
        "g" to "Gr", "gr" to "Gr", "grs" to "Gr", "gramo" to "Gr", "gramos" to "Gr",
        "l" to "Litro", "lt" to "Litro", "lts" to "Litro", "litro" to "Litro", "litros" to "Litro",
        "ml" to ML, "cc" to ML,
        "docena" to "Docena", "docenas" to "Docena",
        // Envases: se cuentan como unidades
        "u" to "Unidad", "un" to "Unidad", "unidad" to "Unidad", "unidades" to "Unidad",
        "paquete" to "Unidad", "paquetes" to "Unidad", "paq" to "Unidad", "caja" to "Unidad", "cajas" to "Unidad",
        "lata" to "Unidad", "latas" to "Unidad", "botella" to "Unidad", "botellas" to "Unidad", "pack" to "Unidad",
        "packs" to "Unidad", "bolsa" to "Unidad", "bolsas" to "Unidad", "frasco" to "Unidad", "frascos" to "Unidad",
    )
    private const val ML = "ml"

    private val NUMBER_WORDS = mapOf(
        "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "media" to 0.5, "medio" to 0.5,
    )

    // Viñetas, checks, numeracion y emojis al inicio de la linea: "- ", "• ", "✅ ", "[ ] ", "1. ", "2) "
    private val LEADING_JUNK = Regex("""^(?:[\s\-*•·–—>+✅✔☑☐⬜🔲▪◦]|\[[ xX✓]?]|\d+[.)]\s|[\p{So}\p{Sk}️‍])+""")
    private val NUMBER = """(\d+(?:[.,]\d+)?|½|¼)"""
    // La posible unidad tiene que ser una palabra completa (evita partir "leches" en "leche" + "s")
    private val QTY_PREFIX = Regex("""^$NUMBER\s*(?:([a-zA-Z]+)\.?(?=\s))?\s*(?:de\s+)?(.+)$""", RegexOption.IGNORE_CASE)
    private val QTY_SUFFIX_X = Regex("""^(.+?)\s*[xX×]\s*$NUMBER\s*([a-zA-Z]+)?$""")
    private val QTY_SUFFIX = Regex("""^(.+?)\s+$NUMBER\s*([a-zA-Z]+)$""")

    /** Limpia y separa cantidad/unidad. Devuelve null si la linea no tiene un producto. */
    fun parseLine(raw: String): ParsedItem? {
        val item = parseLineRaw(raw) ?: return null
        // La app no tiene ml: 500 ml -> 0.5 Litro
        return if (item.unit == ML) item.copy(quantity = item.quantity / 1000.0, unit = "Litro") else item
    }

    private fun parseLineRaw(raw: String): ParsedItem? {
        var line = raw.trim().replace(LEADING_JUNK, "").trim()
        line = line.trimEnd('.', ',', ';', ':').trim()
        if (line.isBlank() || line.none { it.isLetter() }) return null

        // "dos docenas de huevos", "media docena de huevos"
        val firstWord = line.substringBefore(' ').lowercase()
        NUMBER_WORDS[firstWord]?.let { n ->
            val rest = line.substringAfter(' ', "").trim()
            if (rest.isNotBlank()) line = "${formatNumber(n)} $rest"
        }

        QTY_PREFIX.matchEntire(line)?.let { m ->
            val qty = parseNumber(m.groupValues[1])
            val unitWord = m.groupValues[2].lowercase().trimEnd('.')
            val rest = m.groupValues[3].trim()
            val unit = UNIT_WORDS[unitWord]
            return when {
                unit != null && rest.isNotBlank() -> ParsedItem(cleanName(rest), qty, unit)
                unitWord.isEmpty() -> ParsedItem(cleanName(rest), qty, "Unidad")
                // "2 leches": la palabra no era unidad, es parte del nombre
                else -> ParsedItem(cleanName("${m.groupValues[2]} $rest"), qty, "Unidad")
            }
        }
        QTY_SUFFIX_X.matchEntire(line)?.let { m ->
            val unit = UNIT_WORDS[m.groupValues[3].lowercase()] ?: "Unidad"
            return ParsedItem(cleanName(m.groupValues[1]), parseNumber(m.groupValues[2]), unit)
        }
        QTY_SUFFIX.matchEntire(line)?.let { m ->
            val unit = UNIT_WORDS[m.groupValues[3].lowercase()]
            if (unit != null) return ParsedItem(cleanName(m.groupValues[1]), parseNumber(m.groupValues[2]), unit)
        }
        return ParsedItem(cleanName(line), 1.0, "Unidad")
    }

    /** Sugiere categoria y emoji. null si no hay ninguna palabra conocida. */
    fun classify(name: String): Suggestion? {
        val words = normalize(name).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        var best: Entry? = null
        var bestLen = 0
        var bestPos = Int.MAX_VALUE
        for (entry in DICTIONARY) {
            val pos = indexOf(words, entry.tokens)
            if (pos < 0) continue
            val len = entry.tokens.size
            // Gana la frase mas larga; a igual largo, la que aparece primero ("salsa de tomate" -> salsa)
            if (len > bestLen || (len == bestLen && pos < bestPos)) {
                best = entry; bestLen = len; bestPos = pos
            }
        }
        return best?.let { Suggestion(it.category, it.emoji) }
    }

    fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text.lowercase().replace('ñ', '\u0001'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('\u0001', 'ñ')
        return noAccents.replace(Regex("[^a-zñ0-9]+"), " ").trim()
    }

    private fun indexOf(words: List<String>, tokens: List<String>): Int {
        if (tokens.size > words.size) return -1
        for (start in 0..words.size - tokens.size) {
            if (tokens.indices.all { i -> sameWord(words[start + i], tokens[i]) }) return start
        }
        return -1
    }

    /** Coincidencia tolerante a plurales: tomate/tomates, limon/limones, nuez/nueces. */
    private fun sameWord(word: String, keyword: String): Boolean =
        word == keyword || word == keyword + "s" || word == keyword + "es" ||
            (keyword.endsWith("z") && word == keyword.dropLast(1) + "ces") ||
            (keyword.endsWith("s") && word == keyword.dropLast(1))

    private fun parseNumber(s: String): Double = when (s) {
        "½" -> 0.5
        "¼" -> 0.25
        else -> s.replace(',', '.').toDoubleOrNull() ?: 1.0
    }.coerceAtLeast(0.01)

    private fun formatNumber(n: Double) = if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()

    private fun cleanName(s: String): String {
        val trimmed = s.trim().trimEnd('.', ',', ';', ':').trim()
        return trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
