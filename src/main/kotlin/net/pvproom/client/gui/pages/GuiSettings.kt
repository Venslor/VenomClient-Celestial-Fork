package net.pvproom.client.gui.pages

import com.formdev.flatlaf.FlatDarkLaf
import org.apache.commons.io.FileUtils
import net.pvproom.client.*
import net.pvproom.client.event.EventManager
import net.pvproom.client.event.EventTarget
import net.pvproom.client.event.impl.APIReadyEvent
import net.pvproom.client.event.impl.ChangeConfigEvent
import net.pvproom.client.game.addon.LunarCNMod
import net.pvproom.client.game.addon.WeaveMod
import net.pvproom.client.gui.GuiLauncher
import net.pvproom.client.gui.Language
import net.pvproom.client.gui.dialogs.ArgsConfigDialog
import net.pvproom.client.gui.dialogs.LunarQTDialog
import net.pvproom.client.gui.dialogs.MirrorDialog
import net.pvproom.client.gui.layouts.VerticalFlowLayout
import net.pvproom.client.utils.*
import net.pvproom.client.utils.OSEnum.Companion.current
import net.pvproom.client.utils.lunar.LauncherData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.util.*
import javax.swing.*
import javax.swing.JSpinner.DefaultEditor
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder
import javax.swing.event.ChangeEvent
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.plaf.basic.BasicScrollBarUI

private val log: Logger = LoggerFactory.getLogger(GuiSettings::class.java)

class GuiSettings : JScrollPane(panel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED) {

    init {
        EventManager.register(this)

        border = CompoundBorder(
            LineBorder(Color(60, 60, 70), 1, true),
            EmptyBorder(20, 25, 20, 25)
        )

        background = Color(25, 25, 30)

        panel.layout = VerticalFlowLayout(VerticalFlowLayout.LEFT)
        verticalScrollBar.unitIncrement = 30

        verticalScrollBar.ui = object : BasicScrollBarUI() {
            override fun configureScrollBarColors() {
                thumbColor = Color(100, 100, 110)
                trackColor = Color(45, 45, 50)
            }

            override fun createDecreaseButton(orientation: Int) = createZeroButton()
            override fun createIncreaseButton(orientation: Int) = createZeroButton()

            private fun createZeroButton(): JButton {
                val button = JButton()
                button.preferredSize = Dimension(0, 0)
                button.isOpaque = false
                button.isFocusable = false
                button.isBorderPainted = false
                return button
            }
        }

        viewport.background = Color(25, 25, 30)

        initGui()
    }

    /**
     * Hot reload config when API address changes.
     */
    @EventTarget
    suspend fun onChangeConfig(e: ChangeConfigEvent<*>) {
        if (e.configObject is APIConfig && e.key == "address") {
            log.info("API changed, hot reloading...")

            val newApi = e.newValue as? String ?: run {
                log.error("New API value is not a String!")
                return
            }

            try {
                launcherData = LauncherData(newApi)
                metadata = launcherData.metadata()
                log.info("API is ready.")
                APIReadyEvent().call()
            } catch (ex: Exception) {
                log.error("Failed to apply API: $newApi", ex)

                // Rollback the change
                e.cancel()

                JOptionPane.showMessageDialog(
                    this,
                    f.format("gui.settings.launcher.api.error.connection.message", newApi),
                    f.getString("gui.settings.launcher.api.error.connection.title"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun initGui() {
        panel.apply {
            val titleLabel = JLabel(f.getString("gui.settings.title")).apply {
                foreground = Color(200, 200, 255)
                font = Font("Segoe UI", Font.BOLD, 20)
                alignmentX = Component.LEFT_ALIGNMENT
                border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
            }
            add(titleLabel)

            val warningIcon = UIManager.getIcon("OptionPane.warningIcon")
            val warningLabel = JLabel(f.getString("gui.settings.warn.restart"), warningIcon, JLabel.LEFT).apply {
                foreground = Color(255, 100, 100)
                font = Font("Segoe UI", Font.BOLD, 14)
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(255, 100, 100), 1, true),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
                )
                background = Color(50, 20, 20)
                isOpaque = true
                alignmentX = Component.LEFT_ALIGNMENT
            }
            add(warningLabel)

            add(createFoldersPanel())

            add(createJvmPanel())

            add(createLauncherPanel())

            add(createGamePanel())
        }
    }

    private fun createFoldersPanel() = JPanel().apply {
        val folders = mapOf(
            "gui.settings.folder.main" to configDir,
            "gui.settings.folder.theme" to themesDir,
            "gui.settings.folder.log" to launcherLogFile.parentFile,
            "gui.settings.folder.game" to config.installationDir.toFile()
        )

        folders.forEach { (key, dir) ->
            add(createButtonOpenFolder(f.getString(key), dir))
        }
    }

    private fun createJvmPanel() = JPanel().apply {
        layout = VerticalFlowLayout(VerticalFlowLayout.LEFT)
        border = createTitledBorder("gui.settings.jvm")

        add(createJreSelectionPanel())

        add(createRamPanel())

        add(createWrapperPanel())
    }

    private fun createJreSelectionPanel() = JPanel().apply {
        add(JLabel(f.getString("gui.settings.jvm.jre")))

        val customJre = config.jre
        val btnSelectPath = JButton(customJre.ifEmpty { currentJavaExec.path })
        val btnUnset = JButton(f.getString("gui.settings.jvm.jre.unset"))

        btnSelectPath.onClick { selectJavaExecutable(btnSelectPath) }
        btnUnset.onClick { unsetJavaExecutable(btnSelectPath) }

        add(btnSelectPath)
        add(btnUnset)
    }

    private fun createRamPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
        panel.background = Color(36, 36, 36)

        val titleLabel = JLabel(f.getString("gui.settings.jvm.ram"))
        titleLabel.foreground = Color.WHITE
        titleLabel.font = Font("Segoe UI", Font.BOLD, 16)
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT

        val labelRam = JLabel()
        labelRam.foreground = Color(200, 200, 200)
        labelRam.font = Font("Segoe UI", Font.PLAIN, 14)
        labelRam.alignmentX = Component.LEFT_ALIGNMENT

        val ramSlider = JSlider(JSlider.HORIZONTAL, 0, totalMem, config.game.ram).apply {
            background = Color(36, 36, 36)
            foreground = Color(100, 149, 237) // Light blue
            paintTicks = true
            majorTickSpacing = 1024
            maximumSize = Dimension(300, 50)
        }

        ramSlider.addChangeListener { e ->
            val value = ramSlider.value
            val isAdjusting = ramSlider.valueIsAdjusting

            labelRam.text = "${DecimalFormat("#.##").format(value / 1024f)} GB"

            if (!isAdjusting) {
                log.info("Set RAM -> $value")
                config.game.ram = value
            }
        }

        labelRam.text = "${DecimalFormat("#.##").format(ramSlider.value / 1024f)} GB"

        val sliderPanel = JPanel()
        sliderPanel.layout = BorderLayout()
        sliderPanel.background = Color(36, 36, 36)
        sliderPanel.add(ramSlider, BorderLayout.CENTER)
        sliderPanel.add(labelRam, BorderLayout.SOUTH)
        sliderPanel.alignmentX = Component.LEFT_ALIGNMENT

        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(10))
        panel.add(sliderPanel)

        return panel
    }


    private fun createWrapperPanel() = JPanel().apply {
        add(JLabel(f.getString("gui.settings.jvm.wrapper")))
        add(getAutoSaveTextField(config.game, "wrapper"))

        val btnSetVMArgs = JButton(f.getString("gui.settings.jvm.args"))
        btnSetVMArgs.onClick {
            ArgsConfigDialog("vmArgs", config.game).isVisible = true
        }
        add(btnSetVMArgs)
    }

    private fun createLauncherPanel() = JPanel().apply {
        layout = VerticalFlowLayout(VerticalFlowLayout.LEFT)
        border = createTitledBorder("gui.settings.launcher")

        add(createThemePanel())

        add(createPanel {
            add(JLabel(f.getString("gui.settings.launcher.language")))
            add(getAutoSaveComboBox(config, "language", Language.entries.toList()))
        })

        add(createPanel {
            add(JLabel(f.getString("gui.settings.launcher.max-threads")))
            add(getAutoSaveSpinner(config, "maxThreads", 1.0, 256.0, 1.0, true))
        })

        add(createDirectorySelectionPanel(
            "gui.settings.launcher.installation",
            config.installationDir,
            { config.installationDir = it },
            "gui.settings.installation.success"
        ))

        add(createDirectorySelectionPanel(
            "gui.settings.launcher.game",
            config.game.gameDir,
            { config.game.gameDir = it },
            "gui.settings.game-dir.success"
        ))

        add(createPanel {
            add(JLabel("Discord’u başlangıçta aç"))
            add(getAutoSaveCheckBox(config.openDiscord, "openDiscordOnStart", "Aç"))
        })

        //add(createAddonLoadersPanel())
    }

    private fun createThemePanel() = JPanel().apply {
        add(JLabel(f.getString("gui.settings.launcher.theme")))

        val themes = buildList {
            addAll(listOf("dark", "light", "unset"))
            themesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                ?.mapTo(this) { it.name }
        }

        add(getAutoSaveComboBox(config, "theme", themes))

        val btnAddTheme = JButton(f.getString("gui.settings.launcher.theme.add"))
        btnAddTheme.onClick { addCustomTheme() }
        add(btnAddTheme)
    }

    private fun createGamePanel() = JPanel().apply {
        layout = VerticalFlowLayout(VerticalFlowLayout.LEFT)
        border = createTitledBorder("gui.settings.game")

        add(createPanel {
            val btnProgramArgs = JButton(f.getString("gui.settings.game.args"))
            btnProgramArgs.onClick {
                ArgsConfigDialog("args", config.game).isVisible = true
            }
            add(btnProgramArgs)
        })

        add(createResizePanel())
    }

    private fun createResizePanel() = JPanel().apply {
        border = createTitledBorder("gui.settings.game.resize")
        layout = VerticalFlowLayout(VerticalFlowLayout.LEFT)

        val dimensions = listOf(
            "width" to "gui.settings.game.resize.width",
            "height" to "gui.settings.game.resize.height"
        )

        dimensions.forEach { (property, labelKey) ->
            add(createPanel {
                add(JLabel(f.getString(labelKey)))
                add(getAutoSaveSpinner(config.game.resize, property, 10.0, 5000.0, 1.0))
            })
        }
    }

    private fun JButton.onClick(action: (ActionEvent) -> Unit) {
        addActionListener(action)
    }

    private fun JRadioButton.onClick(action: () -> Unit) {
        addActionListener { action() }
    }

    private fun JSlider.onValueChange(action: (Int, Boolean) -> Unit) {
        addChangeListener { e ->
            val source = e.source as JSlider
            action(source.value, source.valueIsAdjusting)
        }
    }

    private fun createPanel(init: JPanel.() -> Unit) = modernPanel(init)

    private fun createTitledBorder(titleKey: String): Border {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color(60, 60, 60)),
                f.getString(titleKey),
                TitledBorder.LEFT,
                TitledBorder.TOP,
                Font("Segoe UI", Font.BOLD, 13),
                Color(180, 180, 180)
            ),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        )
    }

    private fun createDirectorySelectionPanel(
        labelKey: String,
        currentPath: String,
        onPathSelected: (String) -> Unit,
        successMessageKey: String
    ) = JPanel().apply {
        add(JLabel(f.getString(labelKey)))

        val btnSelect = JButton(currentPath)
        btnSelect.onClick {
            chooseFolder()?.let { file ->
                onPathSelected(file.path)
                log.info("Set directory to $file")
                btnSelect.text = file.path
                GuiLauncher.statusBar.text = f.format(successMessageKey, file)
            }
        }

        add(btnSelect)
    }

    private fun selectJavaExecutable(button: JButton) {
        val filter = if (current == OSEnum.Windows) {
            FileNameExtensionFilter("Java Executable", "exe")
        } else null

        chooseFile(filter)?.let { file ->
            GuiLauncher.statusBar.text = f.format("gui.settings.jvm.jre.success", file)
            config.jre = file.path
            button.text = file.path
        }
    }

    private fun unsetJavaExecutable(button: JButton) {
        val confirmed = JOptionPane.showConfirmDialog(
            this,
            f.getString("gui.settings.jvm.jre.unset.confirm"),
            "Confirm",
            JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION

        if (confirmed) {
            button.text = currentJavaExec.path
            config.jre = ""
            GuiLauncher.statusBar.text = f.getString("gui.settings.jvm.jre.unset.success")
        }
    }

    private fun addCustomTheme() {
        val file = chooseFile(FileNameExtensionFilter("Intellij IDEA theme (.json)", "json"))
            ?: return

        val destination = File(themesDir, file.name)

        if (destination.exists()) {
            JOptionPane.showMessageDialog(
                this,
                f.getString("gui.settings.launcher.theme.exist"),
                "File already exists",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        try {
            FileUtils.copyFile(file, destination)
            GuiLauncher.statusBar.text = f.getString("gui.settings.launcher.theme.success")
        } catch (ex: IOException) {
            log.error("Failed to copy theme file", ex)
            throw RuntimeException(ex)
        }
    }

    private fun selectLunarQTInstallation(button: JButton) {
        val file = chooseFile(FileNameExtensionFilter("LunarQT Agent (*.jar)", "jar"))
            ?: return

        config.addon.lcqt.installationDir = file.path
        log.info("Set lcqt-installation to $file")
        button.text = file.path
        GuiLauncher.statusBar.text = f.format("gui.settings.addons.lcqt.success", file)
    }

    private fun getSelectInstallationButton(installation: File, name: String, type: String): JButton {
        val btnSelectLunarCNInstallation = JButton(installation.path)
        btnSelectLunarCNInstallation.addActionListener { e: ActionEvent ->
            val file = saveFile(FileNameExtensionFilter(name, "jar")) ?: return@addActionListener
            val source = e.source as JButton
            source.text = file.path
            setModLoaderInstallation(type, file)
        }
        return btnSelectLunarCNInstallation
    }

    private fun setModLoaderInstallation(key: String, file: File) {
        val addon = config.addon::class.java.getDeclaredMethod("get${getKotlinName(key)}")
            .invoke(config.addon) as AddonLoaderConfiguration
        addon.installationDir = file.path
    }

    /**
     * Toggle loader state based on type.
     *
     * @param type One of: null, "cn", "weave"
     */
    private fun toggleLoader(type: String?) {
        val addon = config.addon
        val weaveState = type == "weave"
        val cnState = type == "cn"

        addon.weave.state = weaveState
        addon.lunarcn.state = cnState
    }

    /**
     * Checks if the loader is selected for a given type.
     * Also enforces that Weave and LunarCN cannot be enabled together.
     */
    private fun isLoaderSelected(type: String?): Boolean {
        val addon = config.addon
        val weaveSelected = addon.weave.state
        val cnSelected = addon.lunarcn.state

        if (weaveSelected && cnSelected && type != null) {
            log.warn("Weave cannot load with LunarCN, auto corrected")
            addon.weave.state = false
            addon.lunarcn.state = false
            return isLoaderSelected(null)
        }

        return when (type) {
            null -> !(weaveSelected || cnSelected)
            "weave" -> weaveSelected
            "cn" -> cnSelected
            else -> false
        }
    }

    /**
     * Creates a JComboBox which auto-saves selection changes to the given object's property.
     */
    private fun getAutoSaveComboBox(obj: Any, key: String, items: List<*>): JComboBox<Any> {
        val comboBox = JComboBox<Any>()

        val isLanguage = items.any { it is Language }
        items.forEach { comboBox.addItem(it) }

        val currentValue = if (isLanguage) obj.getKotlinField<Language>(key) else obj.getKotlinField<String>(key)
        comboBox.selectedItem = currentValue

        comboBox.addActionListener { event ->
            val source = event.source as JComboBox<*>
            source.selectedItem?.let { selected ->
                if (isLanguage) obj.saveConfig(key, selected as Language)
                else obj.saveConfig(key, selected.toString())
            }
        }

        return comboBox
    }

    private fun modernPanel(init: JPanel.() -> Unit) = JPanel().apply {
        layout = VerticalFlowLayout(VerticalFlowLayout.LEFT, 10, 10)
        background = Color(35, 35, 35)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        init()
    }

    companion object {
        private val panel = JPanel()

        /**
         * Creates a JSpinner that auto-saves its value to the given object's property.
         */
        private fun getAutoSaveSpinner(
            obj: Any,
            key: String,
            min: Double,
            max: Double,
            step: Double,
            forceInt: Boolean = false,
            checkAction: (AutoSaveCallback<Number>) -> Unit = {}
        ): JSpinner {
            val initialValue = obj.getKotlinField<Double>(key)
            val spinner = JSpinner(SpinnerNumberModel(initialValue, min, max, step)).apply {
                autoscrolls = true
            }

            val textField = (spinner.editor as DefaultEditor).textField
            textField.columns = 20

            spinner.addChangeListener { event ->
                val source = event.source as JSpinner
                val newValue = if (forceInt) (source.value as Number).toInt() else source.value as Number
                val callback = AutoSaveCallback(newValue, key, obj, source)
                checkAction(callback)
                callback.apply()
            }

            return spinner
        }

        /**
         * Creates a JCheckBox that auto-saves its state to the given object's property.
         */
        private fun getAutoSaveCheckBox(
            obj: Any,
            key: String,
            text: String,
            checkAction: (AutoSaveCallback<Boolean>) -> Unit = {}
        ): JCheckBox {
            val checkBox = JCheckBox(text).apply {
                isSelected = obj.getKotlinField(key)
                addActionListener { event ->
                    val source = event.source as JCheckBox
                    val callback = AutoSaveCallback(source.isSelected, key, obj, source)
                    checkAction(callback)
                    callback.apply()
                }
            }
            return checkBox
        }

        /**
         * Creates a JTextField that auto-saves its content to the given object's property on focus lost.
         */
        private fun getAutoSaveTextField(
            obj: Any,
            key: String,
            checkAction: (AutoSaveCallback<String>) -> Unit = {}
        ): JTextField {
            val value = obj.getKotlinField<String>(key)
            return JTextField(value).apply {
                addFocusListener(object : FocusAdapter() {
                    override fun focusLost(e: FocusEvent) {
                        val text = (e.source as JTextField).text
                        val callback = AutoSaveCallback(text, key, obj, e.source as JTextField)
                        checkAction(callback)
                        callback.apply()
                    }
                })
            }
        }
    }

    /** Validates if the version string matches the pattern like "1.2.3" or "1.2.3-alpha" */
    fun isValidVersion(version: String): Boolean {
        val regex = """^\d+\.\d+\.\d+(-[a-zA-Z0-9\-]+)?$""".toRegex()
        return regex.matches(version)
    }

    /**
     * Callback class for auto-saving UI component changes.
     */
    class AutoSaveCallback<T>(
        var value: T,
        private val key: String,
        private val obj: Any,
        private val source: Any
    ) {
        private val oldValue: T = obj.getKotlinField(key)

        fun revert() {
            value = oldValue
        }

        fun apply() {
            when (source) {
                is JTextField -> {
                    source.text = value as String
                    obj.saveConfig(key, value as String)
                }
                is JCheckBox -> {
                    source.isSelected = value as Boolean
                    obj.saveConfig(key, value as Boolean)
                }
                is JSpinner -> {
                    source.value = value as Number
                    obj.saveConfig(key, value as Number)
                }
            }
        }
    }
}

/**
 * Extension function to save a config property with logging and event calling.
 */
private inline fun <reified T> Any.saveConfig(name: String, value: T?) {
    log.debug("Saving {} (key={}, value={})", this.javaClass.name, name, value)
    if (!ChangeConfigEvent(this, name, value, this.getKotlinField(name)).call()) {
        this.setKotlinField(name, value)
    }
}
