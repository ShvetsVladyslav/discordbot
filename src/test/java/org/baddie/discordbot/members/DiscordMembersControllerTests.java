package org.baddie.discordbot.members;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiscordMembersControllerTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DiscordMembersService.class, () -> mock(DiscordMembersService.class))
            .withUserConfiguration(DiscordMembersController.class);

    @Test
    void localEnvironmentRegistersController() {
        runner.withPropertyValues("APP_ENV=local").run(context ->
                assertThat(context).hasSingleBean(DiscordMembersController.class));
    }

    @Test
    void productionEnvironmentDoesNotRegisterController() {
        runner.withPropertyValues("APP_ENV=prod").run(context ->
                assertThat(context).doesNotHaveBean(DiscordMembersController.class));
    }

    @Test
    void otherEnvironmentsDoNotRegisterController() {
        runner.withPropertyValues("APP_ENV=staging").run(context ->
                assertThat(context).doesNotHaveBean(DiscordMembersController.class));
    }
}
