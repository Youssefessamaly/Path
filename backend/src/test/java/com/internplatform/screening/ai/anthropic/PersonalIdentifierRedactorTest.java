package com.internplatform.screening.ai.anthropic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalIdentifierRedactorTest {

    private final PersonalIdentifierRedactor redactor = new PersonalIdentifierRedactor();

    @Test
    void redactsEmailAddress() {
        String result = redactor.redact("Contact: ivy.nasser@example.com for details.");
        assertThat(result).doesNotContain("ivy.nasser@example.com").contains("[REDACTED_EMAIL]");
    }

    @Test
    void redactsEgyptianMobileNumber() {
        String result = redactor.redact("Phone: 01023456789 or +201023456789.");
        assertThat(result).doesNotContain("01023456789").doesNotContain("+201023456789")
                .contains("[REDACTED_PHONE]");
    }

    @Test
    void redactsLinkedinUrl() {
        String result = redactor.redact("Profile: https://www.linkedin.com/in/ivy-nasser/");
        assertThat(result).doesNotContain("linkedin.com").contains("[REDACTED_URL]");
    }

    @Test
    void neverAltersBodyTextOutsideContactInfo() {
        String body = "WORK EXPERIENCE: Built REST APIs using Java and Spring Boot at a startup internship.";
        String result = redactor.redact(body);
        assertThat(result).isEqualTo(body);
    }

    @Test
    void doesNotMistakeADateRangeForAPhoneNumber() {
        String body = "EDUCATION: Cairo University, 2020-2024.";
        String result = redactor.redact(body);
        assertThat(result).isEqualTo(body);
    }

    @Test
    void preservesArabicTextUntouched() {
        String body = "الخبرة العملية: قمت ببناء واجهات برمجية باستخدام جافا وسبرينج بوت.";
        String result = redactor.redact(body);
        assertThat(result).isEqualTo(body);
    }

    @Test
    void handlesNullAndBlankGracefully() {
        assertThat(redactor.redact(null)).isNull();
        assertThat(redactor.redact("")).isEmpty();
    }
}
