package vent.command.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vent.command.service.RemoteCommandIssuedEvent;
import vent.command.service.RemoteCommandSender;

/**
 * 원격 명령 저장 완료 이벤트 수신 처리기.
 *
 * <p>명령을 보냄(SENT)으로 저장한 트랜잭션이 커밋된 뒤에 게이트웨이로 발행한다(#8). 저장이 롤백되면 발행하지 않는다.
 * 트랜잭션 밖에서 발행된 이벤트는 받지 않는다(fallbackExecution 없음). 이벤트를 내는 서비스는 항상 트랜잭션 안이다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#8)
 */
@Component
public class RemoteCommandIssuedListener {

    private final RemoteCommandSender sender;

    public RemoteCommandIssuedListener(RemoteCommandSender sender) {
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssued(RemoteCommandIssuedEvent event) {
        sender.send(event);
    }
}
