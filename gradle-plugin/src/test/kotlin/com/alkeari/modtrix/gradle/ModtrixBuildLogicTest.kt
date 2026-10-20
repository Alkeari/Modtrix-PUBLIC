package com.alkeari.modtrix.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader

class ModtrixBuildLogicTest {
    private val relocated = embeddedLocator().inputStream().use { relocate(it, mapOf(LOCATOR_PACKAGE to "com.example.mymod.modtrix.locator")) }

    @Test
    fun everyLocatorClassMovesIntoTheModPackage() {
        val classes = relocated.keys.filter { it.endsWith(".class") }
        assertTrue(classes.isNotEmpty())
        assertTrue(classes.all { it.startsWith("com/example/mymod/modtrix/locator/") }, classes.toString())
        assertTrue("com/example/mymod/modtrix/locator/NestedJar.class" in classes)
    }

    @Test
    fun noRelocatedClassStillReferencesTheModtrixPackage() {
        relocated.filterKeys { it.endsWith(".class") }.forEach { (name, bytes) ->
            val text = String(bytes, Charsets.ISO_8859_1)
            assertFalse("com/alkeari/modtrix/locator" in text || "com.alkeari.modtrix.locator" in text, "$name still names the Modtrix package")
            assertEquals(name.removeSuffix(".class"), ClassReader(bytes).className)
        }
    }

    @Test
    fun serviceFilesNameTheRelocatedLocators() {
        assertEquals(
            "com.example.mymod.modtrix.locator.ForgeLocator",
            relocated.getValue("META-INF/services/net.minecraftforge.forgespi.locating.IDependencyLocator").toString(Charsets.UTF_8).trim(),
        )
        assertEquals(
            "com.example.mymod.modtrix.locator.NeoForgeLocator",
            relocated.getValue("META-INF/services/net.neoforged.neoforgespi.locating.IDependencyLocator").toString(Charsets.UTF_8).trim(),
        )
        assertFalse("META-INF/MANIFEST.MF" in relocated)
    }

    @Test
    fun mojangRenamesApplyFromTheVersionThatMadeThem() {
        val source = "ResourceLocation id = key.location(); MyResourceLocationCache c;"
        assertEquals(source, applyMojangRenames(source, "1.21.10"))
        assertEquals("Identifier id = key.identifier(); MyResourceLocationCache c;", applyMojangRenames(source, "1.21.11"))
        assertEquals("Identifier id = key.identifier(); MyResourceLocationCache c;", applyMojangRenames(source, "26.1"))
    }

    @Test
    fun screenDrawingIsRenamedFrom26_1() {
        val source = "public void render(GuiGraphics g, int x) { super.render(g, x); g.drawString(f, t, 1, 2, 3); g.drawCenteredString(f, t, 1, 2, 3); PayloadTypeRegistry.playS2C(); PayloadTypeRegistry.playC2S(); renderer.render(); }"
        assertEquals(source, applyMojangRenames(source, "1.21.11"))
        assertEquals(
            "public void extractRenderState(GuiGraphicsExtractor g, int x) { super.extractRenderState(g, x); g.text(f, t, 1, 2, 3); g.centeredText(f, t, 1, 2, 3); PayloadTypeRegistry.clientboundPlay(); PayloadTypeRegistry.serverboundPlay(); renderer.render(); }",
            applyMojangRenames(source, "26.1"),
        )
    }

    @Test
    fun screenSwitchingMovesToGuiFrom26_2() {
        val source = "minecraft.setScreen(parent);"
        assertEquals(source, applyMojangRenames(source, "26.1.2"))
        assertEquals("minecraft.gui.setScreen(parent);", applyMojangRenames(source, "26.2"))
    }

    @Test
    fun fabricMetadataNamesTheEntrypointAndMixins() {
        val json = metadataTemplate(Loader.FABRIC, "com.example.mymod.fabric.MyModFabric", listOf("mymod.mixins.json"))
        val parsed = groovy.json.JsonSlurper().parseText(json.replace("\${", "{")) as Map<*, *>
        assertEquals(listOf("com.example.mymod.fabric.MyModFabric"), (parsed["entrypoints"] as Map<*, *>)["main"])
        assertEquals(listOf("mymod.mixins.json"), parsed["mixins"])
        assertTrue("\"fabric-api\": \"*\"" in json)
        assertTrue("\"minecraft\": \"\${minecraftRange}\"" in json)
        assertFalse("mixins" in metadataTemplate(Loader.FABRIC, "a.B", emptyList()))
    }

    @Test
    fun neoForgeMetadataNamesItsMixinsAndForgeLeavesThemToTheManifest() {
        val neoforge = metadataTemplate(Loader.NEOFORGE, "unused", listOf("a.mixins.json", "b.mixins.json"))
        assertTrue("[[mixins]]\nconfig = \"a.mixins.json\"" in neoforge.replace("\r\n", "\n"))
        assertTrue("[[mixins]]\nconfig = \"b.mixins.json\"" in neoforge.replace("\r\n", "\n"))
        assertFalse("mixins" in metadataTemplate(Loader.FORGE, "unused", listOf("a.mixins.json")))
        assertFalse("mixins" in metadataTemplate(Loader.NEOFORGE, "unused", emptyList()))
    }

    @Test
    fun forgeAndNeoForgeMetadataUseTheirOwnDependencyFlags() {
        val forge = metadataTemplate(Loader.FORGE, "unused", emptyList())
        val neoforge = metadataTemplate(Loader.NEOFORGE, "unused", emptyList())
        assertEquals(2, Regex("mandatory = true").findAll(forge).count())
        assertTrue("modId = \"forge\"" in forge)
        assertEquals(2, Regex("type = \"required\"").findAll(neoforge).count())
        assertTrue("modId = \"neoforge\"" in neoforge)
        assertTrue("loaderVersion = \"[1,)\"" in neoforge)
        assertTrue("loaderVersion = \"\${forgeLanguageRange}\"" in forge)
    }

    @Test
    fun neoforgeVersionsAreMatchedByTheirMinecraftPrefix() {
        assertEquals("21.1.", neoforgePrefix("1.21.1"))
        assertEquals("21.0.", neoforgePrefix("1.21"))
        assertEquals("26.1.0.", neoforgePrefix("26.1"))
        assertEquals("26.1.1.", neoforgePrefix("26.1.1"))
        val versions = listOf("21.1.9", "21.1.262", "21.1.30", "21.10.1", "26.1.0.18-beta", "26.1.0.19-beta", "26.3.0.83-beta", "26.3.0.83")
        assertEquals("21.1.262", newestNeoForge(versions, "1.21.1"))
        assertEquals("26.1.0.19-beta", newestNeoForge(versions, "26.1"))
        assertEquals("26.3.0.83", newestNeoForge(versions, "26.3"))
    }

    @Test
    fun missingPinsAreFilledAndWrittenOnesStayLocked() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val file = java.io.File(dir, "versions/1.21.1/gradle.properties").apply { parentFile.mkdirs(); writeText("deps.neoforge=21.1.1\n") }
        val fetched = mutableListOf<String>()
        val resolver = PinResolver { url ->
            fetched += url
            when {
                "parchment" in url -> "<metadata><release>2024.11.17</release></metadata>"
                "meta.fabricmc" in url -> """[{"version":"0.20.0-beta","stable":false},{"version":"0.19.5","stable":true}]"""
                "modrinth" in url -> """[{"version_number":"0.116.17+1.21.1"}]"""
                else -> null
            }
        }
        lockPins(dir, mapOf(Loader.FABRIC to listOf("1.21.1"), Loader.NEOFORGE to listOf("1.21.1")), resolver)
        val pins = java.util.Properties().apply { file.reader().use(::load) }
        assertEquals("21.1.1", pins["deps.neoforge"])
        assertEquals("[21.1,)", pins["deps.neoforge_range"])
        assertEquals("0.19.5", pins["deps.fabric_loader"])
        assertEquals("0.116.17+1.21.1", pins["deps.fabric_api"])
        assertEquals("2024.11.17", pins["deps.parchment"])
        assertEquals("21", pins["java.version"])
        assertEquals("48", pins["pack_format"])
        assertTrue(fetched.none { "neoforged" in it })
        fetched.clear()
        lockPins(dir, mapOf(Loader.FABRIC to listOf("1.21.1"), Loader.NEOFORGE to listOf("1.21.1")), resolver)
        assertTrue(fetched.isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun templateNamesAreCamelCase() {
        assertEquals("depsClothConfig", templateName("deps.cloth_config"))
        assertEquals("javaVersion", templateName("java.version"))
        assertEquals("minecraftRangeFabric", templateName("minecraft_range_fabric"))
        assertEquals("modArchivesName", templateName("mod.archives_name"))
    }

    @Test
    fun minecraftRangesMustHaveAnUpperBound() {
        listOf("[1.21.1]", "[1.20.1,1.20.2)", "(1.20,1.21]", "1.21.1", "=1.21.1", ">=1.21 <1.21.2", "1.21 || 1.21.1", ">=26.1 <=26.1.2")
            .forEach { assertTrue(isBoundedRange(it), it) }
        listOf("[1.21.1,)", "(1.20,)", ">=1.21.1", ">1.20", "*", "1.21.x", "~1.21", "^1.21", "", "1.21.1 || >=1.21.2")
            .forEach { assertFalse(isBoundedRange(it), it) }
    }

    @Test
    fun theStarterTakesTheNewModsIdentity() {
        val zip = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z ->
                fun put(name: String, text: String) { z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
                put("common/src/main/java/com/example/mymod/MyMod.java", "package com.example.mymod;\npublic final class MyMod { String ID = \"mymod\"; }")
                put("gradle.properties", "mod.id=mymod\nmod.name=My Mod\nmod.group=com.example\nmod.archives_name=MyMod")
                put("gitignore", "build/")
                put("settings.gradle.kts", "kept by the project")
                put("gradle/wrapper/gradle-wrapper.properties", "kept by the project")
            }
        }.toByteArray()
        val identity = ModIdentity.of("Cool Ores", null, null, "com.alkeari")
        assertEquals(ModIdentity("coolores", "Cool Ores", "com.alkeari", "CoolOres"), identity)
        val files = starterFiles(zip, identity).mapValues { it.value.toString(Charsets.UTF_8) }
        assertEquals(setOf("common/src/main/java/com/alkeari/coolores/CoolOres.java", "gradle.properties", ".gitignore"), files.keys)
        assertEquals("package com.alkeari.coolores;\npublic final class CoolOres { String ID = \"coolores\"; }", files["common/src/main/java/com/alkeari/coolores/CoolOres.java"])
        assertEquals("mod.id=coolores\nmod.name=Cool Ores\nmod.group=com.alkeari\nmod.archives_name=CoolOres", files["gradle.properties"])
    }

    @Test
    fun aVersionWithoutAFabricNodeStillPinsFabricLoaderForCommon() {
        val pins = requiredPins("1.20.5", setOf(Loader.COMMON, Loader.NEOFORGE))
        assertTrue("deps.fabric_loader" in pins)
        assertFalse("deps.fabric_api" in pins)
    }

    @Test
    fun minecraftRangesStayInsideOneRuntimeVariant() {
        val neoforge = listOf("1.20.2", "1.20.4", "1.20.5", "1.21.7")
        assertEquals("1.20.5", crossedBoundary("[1.20.4,1.20.6)", "1.20.4", neoforge))
        assertEquals(null, crossedBoundary("[1.20.4]", "1.20.4", neoforge))
        assertEquals(null, crossedBoundary("[1.20.4,1.20.5)", "1.20.4", neoforge))
        assertEquals("1.20.5", crossedBoundary("[1.20.4,1.20.5]", "1.20.4", neoforge))
        assertEquals("1.20.4", crossedBoundary("[1.20.3,1.20.4]", "1.20.4", neoforge))
        val fabric = listOf("1.20.1", "1.20.2", "1.20.5", "1.21.2")
        assertEquals(null, crossedBoundary(">=1.20.5 <1.21", "1.20.6", fabric))
        assertEquals("1.20.5", crossedBoundary(">=1.20.4 <1.20.6", "1.20.4", fabric))
        assertEquals(null, crossedBoundary("1.21 || 1.21.1", "1.21.1", fabric))
        assertEquals("1.21.2", crossedBoundary(">=1.21 <=1.21.2", "1.21.1", fabric))
    }

    @Test
    fun loaderFloorIsTheLowestVersion() {
        assertEquals("0.14.23", listOf("0.19.2", "0.14.23", "0.15.11").minWith(::compareVersions))
        assertTrue(compareVersions("0.116.12+1.21.1", "0.90.4+1.20.1") > 0)
    }

    @Test
    fun fabricBundleListsEveryNestedJar() {
        val json = fabricBundleJson("mymod", "My \"Mod\"", "1.0.0", "Someone", "LGPL-3.0", hasIcon = false, loaderFloor = "0.14.23",
            jarNames = listOf("MyMod-fabric-1.0.0+1.20.1.jar", "MyMod-fabric-1.0.0+1.21.1.jar"))
        assertTrue("\"id\": \"mymodbundle\"" in json)
        assertTrue("\"name\": \"My \\\"Mod\\\" Bundle\"" in json)
        assertTrue("{ \"file\": \"META-INF/jars/MyMod-fabric-1.0.0+1.20.1.jar\" },\n    { \"file\": \"META-INF/jars/MyMod-fabric-1.0.0+1.21.1.jar\" }" in json)
        assertTrue("\"fabricloader\": \">=0.14.23\"" in json)
        assertFalse("icon" in json)
    }

    private val runtimeEntries = embeddedRuntime().inputStream().use { input ->
        java.util.zip.ZipInputStream(input).use { zip -> generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.map { it.name }.toList() }
    }

    @Test
    fun newestVariantAtOrBelowTheMinecraftVersionWins() {
        val entries = listOf("fabric/1.20.1/java/A.java", "fabric/1.20.5/java/A.java", "fabric/1.20.1/java/B.java", "fabric/1.21.2/java/A.java")
        assertEquals(mapOf("java/A.java" to "fabric/1.20.1/java/A.java", "java/B.java" to "fabric/1.20.1/java/B.java"), selectRuntime(entries, "fabric", "1.20.4"))
        assertEquals("fabric/1.20.5/java/A.java", selectRuntime(entries, "fabric", "1.21.1")["java/A.java"])
    }

    @Test
    fun everyLoaderAndVersionGetsBothHooksAndServiceFiles() {
        for ((side, mc) in listOf("fabric" to "1.20.1", "fabric" to "1.21.1", "forge" to "1.20.1", "neoforge" to "1.20.4", "neoforge" to "1.21.1")) {
            val chosen = selectRuntime(runtimeEntries, side, mc).keys
            assertTrue("resources/META-INF/services/com.alkeari.modtrix.impl.PlatformHooks" in chosen, "$side $mc: $chosen")
            assertTrue("resources/META-INF/services/com.alkeari.modtrix.impl.ClientHooks" in chosen, "$side $mc: $chosen")
        }
        assertTrue("java/com/alkeari/modtrix/api/Network.java" in selectRuntime(runtimeEntries, "common", "1.20.1").keys)
    }

    @Test
    fun runtimeRelocationRenamesServiceFilesAndLeavesTheModAlone() {
        val jar = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(jar).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("META-INF/MANIFEST.MF")); zip.write("Manifest-Version: 1.0\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("META-INF/services/com.alkeari.modtrix.impl.PlatformHooks"))
            zip.write("com.alkeari.modtrix.fabric.FabricPlatformHooks\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("com/alkeari/modtrixexample/Main.class"))
            zip.write(emptyClass("com/alkeari/modtrixexample/Main")); zip.closeEntry()
        }
        val packages = RUNTIME_SUBPACKAGES.associate { "$RUNTIME_PACKAGE.$it" to "com.example.mymod.modtrix.$it" }
        val out = relocate(jar.toByteArray().inputStream(), packages, keepManifest = true)
        assertEquals(
            "com.example.mymod.modtrix.fabric.FabricPlatformHooks",
            out.getValue("META-INF/services/com.example.mymod.modtrix.impl.PlatformHooks").toString(Charsets.UTF_8).trim(),
        )
        assertTrue("META-INF/MANIFEST.MF" in out)
        assertTrue("com/alkeari/modtrixexample/Main.class" in out)
    }

    private fun emptyClass(name: String): ByteArray {
        val writer = org.objectweb.asm.ClassWriter(0)
        writer.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        writer.visitEnd()
        return writer.toByteArray()
    }
}
