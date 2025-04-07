package lab.ysng.snowflake_id_generator.id_generator;

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import lab.ysng.snowflake_id_generator.config.SnowflakeProperties;
import lab.ysng.snowflake_id_generator.utils.TimeUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class SnowflakeIdGenerator implements IdGenerator {

	private final SnowflakeProperties snowflakeProperties;
	// shared mutable state values
	private long lastTimestamp = -1L;
	private long sequence = 0L;
	// lock for ids consistency
	private final ReentrantLock lock = new ReentrantLock();

	/*
	 * 아래의 ID를 생성하는 동작은 3개의 값인
	 * currentTimestamp, sequence, lastTimestamp 에 의해 만들어지므로
	 * 이 값과 연관된 읽기-변경-쓰기 흐름이 끊기면 데이터의 불일치가 발생할 수 있다.
	 * 따라서 세 값과 연관된 부분들 모두 락을 걸어줘야 한다.
	 */
	public long getNextId() {
		lock.lock();
		try {
			long currentTimestamp = TimeUtils.getCurrentTimestamp();
			// 서버시간 설정오류에 의한 아이디 생성 버그 방지를 위한 검증
			if (currentTimestamp < lastTimestamp) {
				throw new RuntimeException("Clock moved backwards. Refusing to generate id");
			}

			// 동일한 밀리세컨드 내에 여러 ID를 생성할 때 충돌 없이 순서를 유지하기 위한 코드
			if (currentTimestamp == lastTimestamp) {
				// sequence 를 1 증가시킨 후 최대값인 4095를 넘지 않게 0 ~ 4095로 순환
				sequence = incrementSequenceWithMask();
				// sequence 가 0이라는 것은 1ms 내에 4096개의 ID를 생성했다는 의미 즉, sequence 가 4095를 넘어서 다시 0이 됐다는 것
				if (sequence == 0) {
					// 시퀀스가 다 찼을 경우 다음 밀리초까지 대기 -> busy waiting loop(시간이 lastTimestamp 보다 커질 때까지 현재 시간을 계속 갱신)
					while (currentTimestamp <= lastTimestamp) {
						currentTimestamp = TimeUtils.getCurrentTimestamp();
					}
				}
			} else {
				// 다음 millisecond 를 넘어갔기 때문에 sequence 를 다시 0부터 시작
				sequence = 0L;
			}
			lastTimestamp = currentTimestamp;

			return composeSnowflakeId(currentTimestamp, sequence);
		} finally {
			lock.unlock();
		}
	}

	private long incrementSequenceWithMask() {
		return (sequence + 1) & snowflakeProperties.getSequenceMask();
	}

	private long composeSnowflakeId(final long currentTimestamp, final long sequence) {
		return ((currentTimestamp - snowflakeProperties.getEpoch()) << snowflakeProperties.getTimestampShift())
			   | (snowflakeProperties.getDatacenterId() << snowflakeProperties.getDatacenterIdShift())
			   | (snowflakeProperties.getWorkerId() << snowflakeProperties.getWorkerIdShift())
			   | sequence;
	}
}
