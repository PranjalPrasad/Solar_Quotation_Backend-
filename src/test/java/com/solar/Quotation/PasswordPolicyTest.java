package com.solar.Quotation;

import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.util.UserPasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private final UserPasswordPolicy policy = new UserPasswordPolicy();

    @Test
    void generatedPasswordAlwaysSatisfiesPolicy() {
        for (int i = 0; i < 1000; i++) {
            String pw = policy.generateTemporary();
            assertEquals(14, pw.length());
            assertTrue(UserPasswordPolicy.RULE.matcher(pw).matches(), "not compliant: " + pw);
        }
    }

    @Test
    void acceptsStrongPassword() {
        assertEquals("Str0ng!Passw0rd", policy.validate("Str0ng!Passw0rd", "rahul.mehta", "rahul@x.com"));
    }

    @Test
    void rejectsWeakPasswords() {
        for (String weak : new String[]{null, "", "short1A!", "alllowercase1!", "ALLUPPERCASE1!",
                "NoDigitsHere!!", "NoSymbol12345A", "Has Space1!aaa"}) {
            assertThrows(BusinessException.class, () -> policy.validate(weak, "someone", "someone@x.com"), weak);
        }
    }

    @Test
    void rejectsPasswordContainingUsernameOrEmailName() {
        assertThrows(BusinessException.class, () -> policy.validate("Rahul.Mehta#2026", "rahul.mehta", "r@x.com"));
        assertThrows(BusinessException.class, () -> policy.validate("Xx-sunita-123A!", "other", "sunita@x.com"));
    }
}