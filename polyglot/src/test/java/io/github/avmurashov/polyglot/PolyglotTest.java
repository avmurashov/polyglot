package io.github.avmurashov.polyglot;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.function.Failable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.text.MessageFormat;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static java.util.ResourceBundle.getBundle;
import static org.apache.commons.lang3.function.Failable.asFunction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PolyglotTest {
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(textBlock = """
            null,                         ,                               messagingInterface
            abstract class,               TestMessagingAbstractClass,     Not an interface: .*TestMessagingAbstractClass
            simple class,                 TestMessagingSimpleClass,       Not an interface: .*TestMessagingSimpleClass
            enum,                         TestMessagingEnum,              Not an interface: .*TestMessagingEnum
            overloaded methods,           TestMessagingOverloadedMethods, Overloaded methods not supported: .*[.]alert[(].*[)] VS .*[.]alert[(].*[)]
            method return type is int,    TestMessagingIntReturnType,     Only String return type is supported: .* int .*[.]greeting[(].*[)]
            """)
    void forMessagingInterfaceValidatesInput(String description, String className, String messagePattern) {
        final var expectedExceptionType = ObjectUtils.isNotEmpty(className)
                ? IllegalArgumentException.class
                : NullPointerException.class;

        final var type = Optional.ofNullable(className)
                .map(cn -> PolyglotTest.class.getName() + "$" + cn)
                .map(asFunction(Class::forName))
                .orElse(null);

        assertThatException().isThrownBy(() -> Polyglot.forMessagingInterface(type))
                .describedAs(description)
                .isInstanceOf(expectedExceptionType)
                .withMessageMatching(messagePattern);
    }

    @Test
    void buildAppliesCertainDefaults() {
        try (var resourceBundleStatic = mockStatic(ResourceBundle.class)) {
            final ArgumentCaptor<String> baseName = ArgumentCaptor.captor();
            final ArgumentCaptor<Locale> locale = ArgumentCaptor.captor();
            final ArgumentCaptor<Module> module = ArgumentCaptor.captor();

            final ResourceBundle resourceBundle = mock();

            resourceBundleStatic.when(() -> getBundle(baseName.capture(), locale.capture(), module.capture()))
                    .thenReturn(resourceBundle);

            try {
                Polyglot.forMessagingInterface(TestMessaging.class).build();
            } catch (RuntimeException e) {
                // noop
            }

            assertThat(locale.getValue()).isSameAs(Locale.getDefault());
            assertThat(baseName.getValue()).isEqualTo(TestMessaging.class.getName() + "ResourceBundle");
            assertThat(module.getValue()).isSameAs(TestMessaging.class.getModule());
        }
    }

    @Test
    void buildProvidesSpecifiedValuesToResourceBundle() {
        try (var resourceBundleStatic = mockStatic(ResourceBundle.class)) {
            final var specifiedBaseName = "test.ResourceBundle";
            final var specifiedModule = Failable.class.getModule();
            final var specifiedLocale = Locale.ROOT;

            final ArgumentCaptor<String> baseName = ArgumentCaptor.captor();
            final ArgumentCaptor<Locale> locale = ArgumentCaptor.captor();
            final ArgumentCaptor<Module> module = ArgumentCaptor.captor();

            final ResourceBundle resourceBundle = mock();

            resourceBundleStatic.when(() -> getBundle(baseName.capture(), locale.capture(), module.capture()))
                    .thenReturn(resourceBundle);

            try {
                Polyglot.forMessagingInterface(TestMessaging.class)
                        .withResourceBundleName(specifiedBaseName)
                        .withResourceBundleModule(specifiedModule)
                        .withLocale(specifiedLocale)
                        .build();
            } catch (RuntimeException e) {
                // noop
            }

            assertThat(locale.getValue()).isSameAs(specifiedLocale);
            assertThat(baseName.getValue()).isEqualTo(specifiedBaseName);
            assertThat(module.getValue()).isSameAs(specifiedModule);
        }
    }

    @Test
    void buildFailsIfResourceBundleDoesNotHaveKeyForTheMethod() {
        try (var resourceBundleStatic = mockStatic(ResourceBundle.class)) {
            final ResourceBundle resourceBundle = mock();
            when(resourceBundle.containsKey("alert")).thenReturn(true);
            when(resourceBundle.getObject("alert")).thenReturn("alert pattern");
            when(resourceBundle.containsKey("greeting")).thenReturn(false);

            resourceBundleStatic.when(() -> getBundle(any(String.class), any(Locale.class), any(Module.class)))
                    .thenReturn(resourceBundle);

            assertThatException().isThrownBy(() -> Polyglot.forMessagingInterface(TestMessaging.class).build())
                    .isInstanceOf(IllegalStateException.class)
                    .withMessageMatching("Resource bundle '.*' lacks key for method: .*greeting.*");
        }
    }

    @Test
    void polyglotImplementationUsesPatternFromLoadedResourceBundleAndSpecifiedLocale() {
        final Map<String, ArgumentCaptor<Object>> formatParams = new HashMap<>();
        final Map<String, Object> formatLocales = new HashMap<>();

        try (
                var resourceBundleStatic = mockStatic(ResourceBundle.class);
                var messageFormatCtor = mockConstruction(MessageFormat.class, (mock, context) -> {
                    final var method = context.arguments().getFirst().toString().replaceFirst("(.*) pattern", "$1");
                    final ArgumentCaptor<Object> formatParam = ArgumentCaptor.captor();
                    when(mock.format(formatParam.capture())).thenReturn(method + " message");
                    formatParams.put(method, formatParam);
                    formatLocales.put(method, context.arguments().getLast());
                })
        ) {
            final ResourceBundle resourceBundle = mock();
            when(resourceBundle.getLocale()).thenReturn(Locale.ENGLISH);
            when(resourceBundle.containsKey("alert")).thenReturn(true);
            when(resourceBundle.getObject("alert")).thenReturn("alert pattern");
            when(resourceBundle.containsKey("greeting")).thenReturn(true);
            when(resourceBundle.getObject("greeting")).thenReturn("greeting pattern");

            resourceBundleStatic.when(() -> getBundle(eq("test.ResourceBundle"), eq(Locale.US), any(Module.class)))
                    .thenReturn(resourceBundle);

            final var testMessaging = Polyglot.forMessagingInterface(TestMessaging.class)
                    .withLocale(Locale.US)
                    .withResourceBundleName("test.ResourceBundle")
                    .build();

            assertThat(formatLocales.get("greeting")).usingRecursiveComparison().isEqualTo(Locale.US);
            assertThat(formatLocales.get("alert")).usingRecursiveComparison().isEqualTo(Locale.US);

            assertThat(testMessaging.greeting("Simon")).isEqualTo("greeting message");
            assertThat(formatParams.get("greeting").getValue())
                    .usingRecursiveComparison()
                    .isEqualTo(new Object[]{"Simon"});


            assertThat(testMessaging.alert("Pumba", LocalTime.parse("16:59"))).isEqualTo("alert message");
            assertThat(formatParams.get("alert").getValue())
                    .usingRecursiveComparison()
                    .isEqualTo(new Object[]{"Pumba", LocalTime.parse("16:59")});
        }
    }

    public abstract static class TestMessagingAbstractClass {
        public abstract String greeting(String testParameter);
        public abstract String alert(String userName, LocalTime time);
    }

    public static class TestMessagingSimpleClass {
        public String greeting(String userName) {
            return "Hi, " + userName + "!";
        }

        public String alert(String userName, LocalTime time) {
            return userName + ", hurry up! It's " + time + " o'clock!";
        }
    }

    public enum TestMessagingEnum {
        INSTANCE;

        public String greeting(String userName) {
            return "Hi, " + userName + "!";
        }

        public String alert(String userName, LocalTime time) {
            return userName + ", hurry up! It's " + time + " o'clock!";
        }
    }

    public interface TestMessagingAlert {
        String alert(String userName, LocalTime time);
    }

    public interface TestMessagingOverloadedMethods extends TestMessagingAlert {
        String greeting(String userName);
        String alert(String userName);
    }

    public interface TestMessagingIntReturnType extends TestMessagingAlert {
        int greeting(String userName);
    }

    public interface TestMessaging extends TestMessagingAlert {
        String greeting(String userName);
    }
}