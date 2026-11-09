package com.tubereturns.security;

import com.tubereturns.model.Role;
import com.tubereturns.model.User;
import com.tubereturns.repository.RoleRepository;
import com.tubereturns.repository.UserRepository;
import com.tubereturns.service.AiModelService;
import com.tubereturns.service.JwtService;
import com.tubereturns.service.PipelineSchedulerService;
import com.tubereturns.service.YouTubeApiService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "tubereturns.rate-limit.enabled=false")
abstract class ApiIntegrationTest {

    static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    WebApplicationContext context;
    @Autowired
    JwtService jwtService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    YouTubeApiService youTubeApiService;
    @MockitoBean
    AiModelService aiModelService;
    @MockitoBean
    PipelineSchedulerService pipelineSchedulerService;

    MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    User userWithRole(String email, String roleName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            User user = new User();
            user.setFirstName("Test");
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setEmailVerified(true);
            user.setRole(role);
            return userRepository.save(user);
        });
    }

    String bearer(String email, String roleName) {
        return "Bearer " + jwtService.generateToken(userWithRole(email, roleName));
    }
}
