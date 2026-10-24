package vent.simulator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 게이트웨이 로컬 버퍼 (원형 버퍼).
 *
 * <p>브로커와 연결이 끊긴 동안 측정값을 쌓아 두었다가, 다시 연결되면 오래된 것부터 보낸다.
 * 저장 공간이 한정된 실제 게이트웨이처럼 크기를 정해 두고, 넘치면 가장 오래된 것부터 버린다(스펙 9.1 C11).
 * 한 게이트웨이의 한 스레드에서만 쓴다.
 *
 * @param <T> 쌓을 항목 타입
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#3)
 */
public class TelemetryBuffer<T> {

    private final int capacity;
    private final Deque<T> items = new ArrayDeque<>();

    /** @param capacity 최대 개수. 30초 주기로 2,880개면 24시간 분량이다 */
    public TelemetryBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("버퍼 크기는 1 이상이어야 한다: " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * 맨 뒤에 넣는다. 가득 차 있으면 가장 오래된 것을 버린다.
     *
     * @return 오래된 항목을 버렸으면 true
     */
    public boolean add(T item) {
        boolean dropped = false;
        if (items.size() == capacity) {
            items.removeFirst();
            dropped = true;
        }
        items.addLast(item);
        return dropped;
    }

    /** 가장 오래된 항목. 보내기 전에 보고, 보낸 뒤에 {@link #removeOldest()}로 지운다. */
    public Optional<T> peekOldest() {
        return Optional.ofNullable(items.peekFirst());
    }

    /** 가장 오래된 항목을 지운다. 보내기에 성공한 뒤에만 부른다. */
    public void removeOldest() {
        items.pollFirst();
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
