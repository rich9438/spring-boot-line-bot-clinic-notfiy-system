package com.everythingcanbe.linebotclinicnotifysystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminMessenger;
import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminWebhookController;

@SpringBootTest
@AutoConfigureMockMvc
class LineBotClinicNotifySystemApplicationTests {

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void adminBotIsDisabledByDefault() throws Exception {
        assertThat(context.getBeanNamesForType(AdminMessenger.class)).isEmpty();
        mockMvc.perform(post(AdminWebhookController.PATH).content("{}")).andExpect(status().isNotFound());
    }

}
