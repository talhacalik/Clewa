package org.calik.clewa.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerIntegrationTest.FailingTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerIntegrationTest {

	@RestController
	static class FailingTestController {

		@GetMapping("/test/fail")
		public String fail() {
			throw new RuntimeException("kasıtlı test hatası");
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void whenControllerThrows_globalExceptionHandlerShouldWrapItInApiResponse() throws Exception {
		mockMvc.perform(get("/test/fail"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("SERVER_ERROR"));
	}

}
