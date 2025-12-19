module io.github.avmurashov.polyglot {
    requires java.base;
    uses java.util.spi.ResourceBundleProvider;

    requires transitive org.apache.commons.lang3;

    exports io.github.avmurashov.polyglot;
}