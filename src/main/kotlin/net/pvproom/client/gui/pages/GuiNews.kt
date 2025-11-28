package net.pvproom.client.gui.pages

import cn.hutool.crypto.SecureUtil
import net.pvproom.client.event.EventManager
import net.pvproom.client.event.EventTarget
import net.pvproom.client.event.impl.APIReadyEvent
import net.pvproom.client.f
import net.pvproom.client.files.DownloadManager.cache
import net.pvproom.client.gui.LauncherAlert
import net.pvproom.client.gui.LauncherBirthday
import net.pvproom.client.gui.LauncherNews
import net.pvproom.client.metadata
import net.pvproom.client.utils.lunar.LauncherBlogpost
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.*
import java.io.IOException
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.swing.*
import javax.swing.border.*
import kotlin.math.abs

class GuiNews : JScrollPane(panel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER) {
    private lateinit var blogPosts: List<LauncherBlogpost>

    init {
        EventManager.register(this)
        border = BorderFactory.createEmptyBorder()

        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = EmptyBorder(25, 25, 25, 25)
        panel.background = Color(28, 28, 34)

        verticalScrollBar.unitIncrement = 20
        verticalScrollBar.background = Color(20, 20, 20)
        viewport.background = Color(24, 24, 30)

        val title = JLabel(f.getString("gui.news.title")).apply { // DO MODERN TOO
            font = Font("Segoe UI", Font.BOLD, 20)
            foreground = Color(200, 200, 255)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(title)
        panel.add(Box.createVerticalStrut(20))
    }

    private fun calcBirthday(): Int {
        val birthday = LocalDate.of(LocalDate.now().year, 7, 29)
        val today = LocalDate.now()
        return ChronoUnit.DAYS.between(today, birthday).toInt()
    }

    @EventTarget
    fun onAPIReady(event: APIReadyEvent) {
        blogPosts = metadata.blogposts
        panel.removeAll()
        initGui()
        panel.revalidate()
        panel.repaint()
    }

    private fun initGui() {
        panel.add(JLabel(f.getString("gui.news.title")).apply {
            font = Font("Segoe UI", Font.BOLD, 20)
            foreground = Color(200, 200, 255)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        panel.add(Box.createVerticalStrut(20))

        try {
            metadata.alert?.let { alert ->
                log.info("${alert.name}: ${alert.text}")
                panel.add(createCardPanel(LauncherAlert(alert)))
            }
        } catch (e: Exception) {
            log.warn("Warning: Load alert failed")
            log.error(e.stackTraceToString())
        }

        val birthday = calcBirthday()
        if (abs(birthday) <= 10) {
            panel.add(Box.createVerticalStrut(15))
            panel.add(createCardPanel(LauncherBirthday(birthday)))
        }

        if (blogPosts.isEmpty()) {
            panel.add(Box.createVerticalStrut(10))
            panel.add(JLabel(f.getString("gui.news.empty")).apply {
                foreground = Color(160, 160, 160)
                font = font.deriveFont(Font.ITALIC, 14f)
                alignmentX = Component.LEFT_ALIGNMENT
            })
        } else {
            for (blogPost in blogPosts) {
                try {
                    if (cache(URL(blogPost.image), "news/${SecureUtil.sha1(blogPost.title)}", false)) {
                        panel.add(Box.createVerticalStrut(15))
                        panel.add(createCardPanel(LauncherNews(blogPost)))
                    }
                } catch (e: IOException) {
                    log.warn("Failed to cache ${blogPost.image}")
                } catch (e: NullPointerException) {
                    panel.add(Box.createVerticalStrut(10))
                    panel.add(JLabel(f.getString("gui.news.official")).apply {
                        foreground = Color(160, 160, 160)
                        font = font.deriveFont(Font.ITALIC, 14f)
                        alignmentX = Component.LEFT_ALIGNMENT
                    })
                    log.warn("Failed to load news ${blogPost.image}")
                }
            }
        }
    }

    private fun createCardPanel(content: JComponent): JPanel {
        return JPanel(BorderLayout()).apply {
            background = Color(36, 36, 46)
            border = CompoundBorder(
                LineBorder(Color(90, 130, 180), 1, true),
                EmptyBorder(18, 20, 18, 20)
            )
            maximumSize = Dimension(Int.MAX_VALUE, content.preferredSize.height + 30)
            add(content, BorderLayout.CENTER)
            isOpaque = true
        }
    }

    companion object {
        private val panel = JPanel()
        private val log: Logger = LoggerFactory.getLogger(GuiNews::class.java)
    }
}

