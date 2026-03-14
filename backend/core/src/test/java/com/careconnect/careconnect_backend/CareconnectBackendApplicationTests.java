package com.careconnect.careconnect_backend;

import com.careconnect.CareconnectBackendApplication;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CareconnectBackendApplicationTests {

	@Test
	void contextLoads() {
		// Verify the application class is properly annotated for Spring Boot
		assertThat(CareconnectBackendApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
	}

	@Test
	void main_callsSpringApplicationRun() {
		try (MockedStatic<SpringApplication> springApp = mockStatic(SpringApplication.class)) {
			springApp.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
					.thenReturn(null);

			CareconnectBackendApplication.main(new String[]{});

			springApp.verify(() -> SpringApplication.run(CareconnectBackendApplication.class, new String[]{}));
		}
	}
}
