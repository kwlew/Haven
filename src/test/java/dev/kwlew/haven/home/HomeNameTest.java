package dev.kwlew.haven.home;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeNameTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void normalizesToLowercase() {
        assertEquals("base", Home.normalize("BASE"));
        assertEquals("my_home-2", Home.normalize("My_Home-2"));
    }

    @Test
    void treatsNullAsEmpty() {
        assertEquals("", Home.normalize(null));
        assertFalse(Home.isValidName(null));
    }

    /**
     * Under tr_TR a locale-sensitive toLowerCase maps 'I' to a dotless 'i', which fails validation
     * and desynchronises the write path from the lookup path. Normalization must use Locale.ROOT.
     */
    @Test
    void normalizationIsLocaleIndependent() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertEquals("i", Home.normalize("I"));
        assertEquals("mine", Home.normalize("MINE"));
        assertTrue(Home.isValidName("MINE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "base", "my-home", "my_home", "home2", "0123456789abcdef"})
    void acceptsValidNames(String name) {
        assertTrue(Home.isValidName(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                    // empty
            "0123456789abcdefg",   // 17 chars, one over the limit
            "my home",             // space
            "my.home",             // dot
            "my/home",             // slash
            "home!",               // punctuation
            "réunion"              // non-ascii
    })
    void rejectsInvalidNames(String name) {
        assertFalse(Home.isValidName(name), name);
    }

    @Test
    void validationAppliesToTheNormalizedForm() {
        assertTrue(Home.isValidName("BASE"), "uppercase input is normalized before validating");
    }
}
