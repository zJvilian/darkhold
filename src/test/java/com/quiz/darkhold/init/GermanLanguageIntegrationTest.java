package com.quiz.darkhold.init;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GermanLanguageIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void germanChoiceRendersTranslatedPageAndPersistsWithoutSession() throws Exception {
        var result = mvc.perform(get("/").param("lang", "de"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"de\"")))
                .andExpect(content().string(containsString(">DE</span>")))
                .andExpect(content().string(containsString("Interaktive Quiz-Plattform"))).andReturn();
        Cookie language = result.getResponse().getCookie("darkhold_language");
        assertNotNull(language);
        assertTrue(language.getMaxAge() > 0);
        mvc.perform(get("/join").cookie(language)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Wähle deinen Avatar")))
                .andExpect(content().string(containsString("Keine E-Mail, kein Passwort, kein Konto nötig.")));
        mvc.perform(get("/login").cookie(language)).andExpect(status().isOk())
                .andExpect(content().string(containsString("E-Mail-Adresse")))
                .andExpect(content().string(containsString("lang=\"de\"")));
    }

    @Test
    void hostEditorAndClientMessagesUseGerman() throws Exception {
        mvc.perform(get("/challenge/create").param("lang", "de").with(user("host")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Grundinformationen")))
                .andExpect(content().string(containsString("window.DarkholdMessages =")))
                .andExpect(model().attribute("clientMessages",
                        hasEntry("client.editor.addQuestion", "Frage hinzufügen")))
                .andExpect(content().string(containsString("Live-Quiz starten")))
                .andExpect(content().string(containsString("lang=\"de\"")));
    }

    @Test
    void explicitEnglishSelectionOverridesGermanCookie() throws Exception {
        mvc.perform(get("/join").cookie(new Cookie("darkhold_language", "de")).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"en\"")))
                .andExpect(content().string(containsString("Pick your avatar")));
    }

    @Test
    void germanBundleCoversEveryEnglishKeyAndPreservesPlaceholders() throws Exception {
        Properties english = bundle("messages.properties");
        Properties german = bundle("messages_de.properties");
        for (String key : english.stringPropertyNames()) {
            assertNotNull(german.getProperty(key), key);
            var placeholders = java.util.regex.Pattern.compile("\\{\\d+}");
            var expected = placeholders.matcher(english.getProperty(key)).results()
                    .map(match -> match.group()).toList();
            var actual = placeholders.matcher(german.getProperty(key)).results()
                    .map(match -> match.group()).toList();
            assertEquals(expected, actual, key);
        }
    }

    private Properties bundle(final String filename) throws Exception {
        Properties properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream(filename);
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
