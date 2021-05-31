package org.acme;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Startup
@Singleton
public class MetricsConfig {
    private final List<Tag> commonTags;

    public MetricsConfig(@ConfigProperty(name = "current.env") String environment,
                         @ConfigProperty(name = "quarkus.application.name") String appName) {
        this.commonTags = Arrays.asList(Tag.of("env", environment), Tag.of("appName", appName),
                                        Tag.of("uuid", instanceUuid()));
    }

    private static String instanceUuid() {
        return UUID.randomUUID().toString();
    }

    @Produces
    @ApplicationScoped
    @Unremovable
    public MeterFilter commonTagsMeterFilter() {
        return MeterFilter.commonTags(commonTags);
    }

    @Produces
    @Singleton
    @Unremovable
    public MeterRegistry graphiteMeterRegistry() {
        MeterRegistry registry = new GraphiteMeterRegistry(this::graphiteConfig, Clock.SYSTEM, this::graphitePrefix);
        registry.config().namingConvention(NamingConvention.identity);
        return registry;
    }

    private String graphitePrefix(Meter.Id id, NamingConvention convention) {
        List<String> commonTagIds = commonTags.stream().map(Tag::getKey).collect(Collectors.toList());
        List<Tag> conventionTags = id.getConventionTags(convention);
        Map<String, String> idTags = conventionTags.stream()
                                                   .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

        List<String> hierarchy = commonTagIds.stream()
                                             .filter(idTags::containsKey)
                                             .map(idTags::get)
                                             .map(this::clearText)
                                             .collect(Collectors.toCollection(LinkedList::new));
        hierarchy.add(id.getConventionName(convention));
        hierarchy.addAll(conventionTags.stream()
                                       .map(Tag::getKey)
                                       .filter(key -> !commonTagIds.contains(key))
                                       .map(idTags::get)
                                       .map(this::clearText)
                                       .collect(Collectors.toList()));

        return String.join(".", hierarchy);
    }

    private String clearText(String text) {
        return text.replaceAll("[{} ]", "_");
    }

    private String graphiteConfig(String property) {
        return ConfigProvider.getConfig()
                             .getOptionalValue(property, String.class)
                             .orElseGet(() -> GraphiteConfig.DEFAULT.get(property));
    }
}
