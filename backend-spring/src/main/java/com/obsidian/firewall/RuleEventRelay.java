package com.obsidian.firewall;

import com.obsidian.firewall.EventEnvelope.Kind;
import com.obsidian.rules.RuleChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes rule changes once their transaction has committed, so daemons never see a rolled-back rule. */
@Component
class RuleEventRelay {

    private final EventPublisher publisher;

    RuleEventRelay(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener
    void onRuleChanged(RuleChanged change) {
        publisher.publish(Kind.RULE, change.rule().getOrgId(), change.rule().getUserId(),
                RuleEvent.of(change.rule(), change.at()));
    }
}
