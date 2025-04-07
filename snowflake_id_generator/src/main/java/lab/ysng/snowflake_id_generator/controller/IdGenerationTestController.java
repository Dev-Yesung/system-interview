package lab.ysng.snowflake_id_generator.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import lab.ysng.snowflake_id_generator.id_generator.IdGenerator;

@RestController
public class IdGenerationTestController {

	private final IdGenerator idGenerator;

	public IdGenerationTestController(
		@Qualifier("lockFreeSnowflakeIdGenerator") final IdGenerator idGenerator
	) {
		this.idGenerator = idGenerator;
	}

	@PostMapping("/api/v1/test/id-generator")
	public ResponseEntity<Long> generateId() {
		final long nextId = idGenerator.getNextId();

		return ResponseEntity.ok(nextId);
	}
}
