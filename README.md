# Polyglot

This library goes beyond the widely known Java I18N to simplify multilingual application development.
It encourages developers to implement localization via the well-defined messaging interfaces.

Suppose you are developing application that welcomes user and then it raises alert at defined time.

Such an application would have the following messages in English:
1. > Hello, {userName}
2. > {userName}, hurry up! It's {alertTime} o'clock!

You definitely know to define corresponding message bundle. Say, it is based on properties and plain-old
`MessageFormat`:
```properties
greeting=Hello, {0}
alert={0}, hurry up! It's {0,time,short} o'clock!
```

Traditionally, you defined the resource bundle, and then, for each message you accessed the pattern and applied the
message format:

```java
final Locale locale;
final String resourceBundleName;
final Module resourceBundleModule;

//...
final var resourceBundle = ResourceBundle.getBundle(resourceBundleName, locale, resourceBundleModule);

//...
final var greetingPattern = resourceBundle.getString("greeting");
final var greetingMessageFormat = new MessageFormat(greetingPattern, locale);
final var greetingMessage = greetingMessageFormat.format(new Object[] { userName });

//...
final var alertPattern = resourceBundle.getString("alert");
final var alertMessageFormat = new MessageFormat(alertPattern, locale);
final var alertMessage = alertMessageFormat.format(new Object[] { userName, alertime});
```

While definitely working, this approach have several drawbacks:
1. There is no single place where all the messages (message keys) are defined, you have to manage them externally
2. There is no control over `Locale`s used for `MessageFormat` and `ResourceBundle`, it's up to you to ensure you are
   using the same `Locale`
3. There is no single place where the types and order of message pattern parameters are defined, and you have to manage 
   them externally.

The Polyglot library fights these issues.

First, you define the messaging interface.

In our case it will look like this:
```java
public interface MyMessages {
    String greeting(String userName);
    String alert(String userName, Instant time);
}
```

Then, you instantiate this messaging interface:
```java
final var myMessages = Poliglot.forMessagingInterface(MyMessages.class).build();
```

Expression above:
1. uses system default `Locale`;
2. loads `ResourceBundle` with name that is the fully qualified class name of the messaging interface, concatenated
   with `"ResourceBundle"` suffix;
3. loads `ResourceBundle` from the module, where the messaging interface is defined.

You may choose to override these defaults:
```java
final Locale locale;
final String resourceBundleName;
final Module resourceBundleModule;

//...
final var myMessages = Poliglot.forMessagingInterface(MyMessages.class)
        .withLocale(locale)
        .withResourceBundleName(resourceBundleName)
        .withResourceBundleModule(resourceBundleModule)
        .build();
```

And finally, you use prepared instance for messaging:
```java
final var greetingMessage = myMessages.greeting(userName);
final var alertMessage = myMessages.alert(userName, alertTime);
```

Please note that there are some constraints on messaging interfaces:
1. Method names in messaging interface define the keys in `ResourceBundle`, and as such - methods MUST NOT be
   overloaded.
2. Types of method parameters SHOULD be supported by the `MessageFormat` patterns used for i18n.
3. Method return type MUST be `java.lang.String`

That's it! Hope you will enjoy using this library!