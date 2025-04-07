package lab.ysng.snowflake_id_generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import lab.ysng.snowflake_id_generator.utils.NumberUtils;
import lombok.Getter;

@Getter
@ConfigurationProperties(prefix = "id-generator.snowflake")
public class SnowflakeProperties {

	private final long epoch;
	private final long minIdValue;
	private final long datacenterId;
	private final long workerId;
	private final int datacenterIdBits;
	private final int workerIdBits;
	private final int sequenceBits;
	private final long maxDatacenterId;
	private final long maxWorkerId;
	private final long sequenceMask;
	private final long workerIdShift;
	private final long datacenterIdShift;
	private final long timestampShift;

	@ConstructorBinding
	public SnowflakeProperties(
		long epoch,
		long minIdValue,
		long datacenterId,
		long workerId,
		int datacenterIdBits,
		int workerIdBits,
		int sequenceBits
	) {
		this.epoch = epoch;
		this.minIdValue = minIdValue;
		this.datacenterId = datacenterId;
		this.workerId = workerId;
		this.datacenterIdBits = datacenterIdBits; // default value: 5bits
		this.workerIdBits = workerIdBits; // default value: 5bits
		this.sequenceBits = sequenceBits; // default value: 12bits
		// 계산 필드 초기화
		this.maxDatacenterId = ~(-1L << datacenterIdBits); // 기본값 기준 최대 31
		this.maxWorkerId = ~(-1L << workerIdBits); // 기본값 기준 최대 31
		this.sequenceMask = ~(-1L << sequenceBits); // 기본값 기준 최대 4095
		this.workerIdShift = sequenceBits;
		this.datacenterIdShift = sequenceBits + workerIdBits;
		this.timestampShift = sequenceBits + workerIdBits + datacenterIdBits;
		validateDatacenterId();
		validateWorkerId();
	}

	private void validateDatacenterId() {
		if (!NumberUtils.isInRange(datacenterId, minIdValue, maxDatacenterId)) {
			throw new IllegalArgumentException("Datacenter ID must be between %d and %d"
				.formatted(minIdValue, maxDatacenterId));
		}
	}

	private void validateWorkerId() {
		if (!NumberUtils.isInRange(workerId, minIdValue, maxWorkerId)) {
			throw new IllegalArgumentException("Worker ID must be between %d and %d"
				.formatted(minIdValue, maxWorkerId));
		}
	}
}
