package lab.ysng.snowflake_id_generator.id_generator;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lab.ysng.snowflake_id_generator.config.SnowflakeProperties;
import lab.ysng.snowflake_id_generator.utils.TimeUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class LockFreeSnowflakeIdGenerator implements IdGenerator {

	private final SnowflakeProperties snowflakeProperties;
	private final AtomicLong lastTimestamp = new AtomicLong(-1L);
	private final AtomicInteger sequence = new AtomicInteger(0);

	/*
	 * 언제 락 없는 방식이 유리한가?
	 * 초당 수천 ~ 수만 개의 ID를 생성해야 하는 경우 (TPS 10,000 이상)
	 * GC Pause(stop the world)를 줄이고 싶은 경우 (Lock 생성/해제 비용 없음)
	 * 다중 코어 환경에서 성능 최대치가 필요한 경우
	 */
	@Override
	public long getNextId() {
		while (true) {
			long currentTimestamp = TimeUtils.getCurrentTimestamp();
			long last = lastTimestamp.get();
			if (currentTimestamp < last) {
				throw new RuntimeException("Clock moved backwards. Refusing to generate id");
			}

			if (currentTimestamp == last) {
				// 동일 밀리초 내
				long seq = sequence.incrementAndGet() & snowflakeProperties.getSequenceMask();
				if (seq == 0L) {
					// 시퀀스가 초과한 경우: 다음 millisecond 까지 기다림
					currentTimestamp = waitNextMillis(currentTimestamp);
					sequence.set(0);
				}
			} else {
				// 새로운 밀리초 시작: 시퀀스 초기화
				sequence.set(0);
			}

			// 타임스탬프 갱신에 성공하면 ID 생성
			if (lastTimestamp.compareAndSet(last, currentTimestamp)) {
				return composeSnowflakeId(currentTimestamp, sequence.get());
			}

			// 실패했으면 다른 쓰레드가 먼저 갱신한 것 -> 루프 반복
		}
	}

	private long waitNextMillis(long timestamp) {
		long now = TimeUtils.getCurrentTimestamp();
		while (now <= timestamp) {
			now = TimeUtils.getCurrentTimestamp();
		}

		return now;
	}

	private long composeSnowflakeId(final long currentTimestamp, final long sequence) {
		return ((currentTimestamp - snowflakeProperties.getEpoch()) << snowflakeProperties.getTimestampShift())
			   | (snowflakeProperties.getDatacenterId() << snowflakeProperties.getDatacenterIdShift())
			   | (snowflakeProperties.getWorkerId() << snowflakeProperties.getWorkerIdShift())
			   | sequence;
	}
}
