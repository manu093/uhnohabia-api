package com.sharedshoppinglists.app.domain.classifier

import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.ALMACEN
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.BEBIDAS
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.CARNICERIA
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.GRANJA
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.LIMPIEZA
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.OTROS
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.PERFUMERIA
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.ParsedItem
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier.VERDULERIA
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ProductClassifierTest : FunSpec({

    context("classify: categoria") {
        withData(
            nameFn = { "${it.first} -> ${it.second}" },
            "Leche" to GRANJA,
            "leche descremada La Serenísima" to GRANJA,
            "Huevos" to GRANJA,
            "queso rallado" to GRANJA,
            "Tomates" to VERDULERIA,
            "limones" to VERDULERIA,
            "Papa" to VERDULERIA,
            "cebolla de verdeo" to VERDULERIA,
            "Carne picada" to CARNICERIA,
            "pechuga de pollo" to CARNICERIA,
            "Milanesas" to CARNICERIA,
            "Pan" to ALMACEN,
            "pan rallado" to ALMACEN,
            "fideos tirabuzón" to ALMACEN,
            "Yerba" to ALMACEN,
            "Dulce de leche" to ALMACEN,          // la frase le gana a "leche"
            "salsa de tomate" to ALMACEN,         // "salsa" aparece antes que "tomate"
            "puré de tomate" to ALMACEN,
            "nueces" to ALMACEN,
            "Coca Cola 2.25" to BEBIDAS,
            "jugo de naranja" to BEBIDAS,
            "cervezas" to BEBIDAS,
            "Agua" to BEBIDAS,
            "Lavandina" to LIMPIEZA,
            "papel higiénico" to LIMPIEZA,
            "Detergente" to LIMPIEZA,
            "jabón en polvo" to LIMPIEZA,
            "Shampoo" to PERFUMERIA,
            "jabón de tocador" to PERFUMERIA,
            "pasta dental" to PERFUMERIA,
            "crema de leche" to GRANJA,
            "crema dental" to PERFUMERIA,
            "pañales" to PERFUMERIA,
            "alimento para perro" to OTROS,
        ) { (name, category) ->
            ProductClassifier.classify(name)?.categoryName shouldBe category
        }
    }

    test("classify: emoji") {
        ProductClassifier.classify("Leche")?.emoji shouldBe "🥛"
        ProductClassifier.classify("bananas")?.emoji shouldBe "🍌"
        ProductClassifier.classify("Yerba mate")?.emoji shouldBe "🧉"
    }

    test("classify: desconocido devuelve null") {
        ProductClassifier.classify("Regalo para Juan").shouldBeNull()
        ProductClassifier.classify("").shouldBeNull()
    }

    test("classify: no confunde palabras parciales") {
        // "sal" no debe matchear "salchicha" ni "salsa"; "te" no debe matchear "tomate"
        ProductClassifier.classify("salchichas")?.categoryName shouldBe CARNICERIA
        ProductClassifier.classify("tomate")?.categoryName shouldBe VERDULERIA
    }

    context("parseLine") {
        withData(
            nameFn = { "'${it.first}'" },
            "Leche" to ParsedItem("Leche", 1.0, "Unidad"),
            "- leche" to ParsedItem("Leche", 1.0, "Unidad"),
            "• Pan" to ParsedItem("Pan", 1.0, "Unidad"),
            "✅ Yerba" to ParsedItem("Yerba", 1.0, "Unidad"),
            "[ ] arroz" to ParsedItem("Arroz", 1.0, "Unidad"),
            "1. Fideos" to ParsedItem("Fideos", 1.0, "Unidad"),
            "2) queso" to ParsedItem("Queso", 1.0, "Unidad"),
            "2 leches" to ParsedItem("Leches", 2.0, "Unidad"),
            "3 tomates" to ParsedItem("Tomates", 3.0, "Unidad"),
            "1 kg papa" to ParsedItem("Papa", 1.0, "Kg"),
            "2kg de papas" to ParsedItem("Papas", 2.0, "Kg"),
            "500 g de carne picada" to ParsedItem("Carne picada", 500.0, "Gr"),
            "1,5 kg asado" to ParsedItem("Asado", 1.5, "Kg"),
            "leche x2" to ParsedItem("Leche", 2.0, "Unidad"),
            "papa 2 kg" to ParsedItem("Papa", 2.0, "Kg"),
            "dos docenas de huevos" to ParsedItem("Huevos", 2.0, "Docena"),
            "media docena de huevos" to ParsedItem("Huevos", 0.5, "Docena"),
            "Coca Cola 2.25" to ParsedItem("Coca Cola 2.25", 1.0, "Unidad"),
            "500 ml de crema" to ParsedItem("Crema", 0.5, "Litro"),
            "2 paquetes de fideos" to ParsedItem("Fideos", 2.0, "Unidad"),
            "  - Pan lactal.  " to ParsedItem("Pan lactal", 1.0, "Unidad"),
        ) { (raw, expected) ->
            ProductClassifier.parseLine(raw) shouldBe expected
        }
    }

    test("parseLine: lineas vacias o sin letras se descartan") {
        ProductClassifier.parseLine("   ").shouldBeNull()
        ProductClassifier.parseLine("---").shouldBeNull()
        ProductClassifier.parseLine("✅").shouldBeNull()
        ProductClassifier.parseLine("123").shouldBeNull()
    }
})
