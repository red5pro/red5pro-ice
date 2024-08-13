/* See LICENSE.md for license information */
package com.red5pro.ice;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements ice4j internal nomination strategies.
 *
 * @author Emil Ivov
 */
public class DefaultNominator implements PropertyChangeListener {

    private final static Logger logger = LoggerFactory.getLogger(DefaultNominator.class);

    /**
     * The Agent that created us.
     */
    private final Agent parentAgent;

    /**
     * The strategy that this nominator should use to nominate valid pairs.
     */
    private NominationStrategy strategy = NominationStrategy.NOMINATE_FIRST_VALID;

    /**
     * Map that will remember association between validated relayed candidate
     * and a timer. It is used with the NOMINATE_FIRST_HIGHEST_VALID strategy.
     */
    private final ConcurrentMap<String, TimerTask> validatedCandidates = new ConcurrentHashMap<>();

    /**
     * Creates a new instance of this nominator using parentAgent as
     * a reference to the Agent instance that we should use to
     * nominate pairs.
     *
     * @param parentAgent the {@link Agent} that created us.
     */
    public DefaultNominator(Agent parentAgent) {
        this.parentAgent = parentAgent;
        parentAgent.addStateChangeListener(this);
        logger.debug("Created a new DefaultNominator with strategy: {}", strategy);
    }

    /**
     * Tracks changes of state in {@link IceMediaStream}s and {@link
     * CheckList}s.
     *
     * @param ev the event that we should use in case it means we should
     * nominate someone.
     */
    public void propertyChange(PropertyChangeEvent ev) {
        logger.debug("Property change event: {}", ev);
        String propertyName = ev.getPropertyName();
        if (IceProcessingState.class.getName().equals(propertyName)) {
            if (ev.getNewValue() != IceProcessingState.RUNNING) {
                return;
            }
            for (IceMediaStream stream : parentAgent.getStreams()) {
                stream.addPairChangeListener(this);
                stream.getCheckList().addStateChangeListener(this);
            }
        }
        // CONTROLLED agents cannot nominate, but only enforce this if trickling is enabled
        if (!parentAgent.isControlling() && parentAgent.isTrickling()) {
            logger.debug("Non-controlling agent, cannot nominate");
            return;
        }
        if (ev.getSource() instanceof CandidatePair) {
            // STUN Usage for Consent Freshness is of no concern here.
            if (IceMediaStream.PROPERTY_PAIR_CONSENT_FRESHNESS_CHANGED.equals(propertyName)) {
                return;
            }
            CandidatePair validPair = (CandidatePair) ev.getSource();
            logger.debug("Valid pair: {}", validPair.toShortString());
            // do not nominate pair if there is currently a selected pair for the component
            if (validPair.getParentComponent().getSelectedPair() != null) {
                logger.debug("Keep-alive for pair: {}", validPair.toShortString());
                return;
            }
            // XXX(paul) special case for ffmpeg-whip, which sends USE-CANDIDATE
            if (parentAgent.isControlling() && !parentAgent.isTrickling() && validPair.useCandidateReceived()) {
                logger.debug("Received USE-CANDIDATE, nominate: {}", validPair.toShortString());
                if (!validPair.isNominated()) {
                    validPair.nominate();
                }
                if (!validPair.isValid()) {
                    parentAgent.getStreams().forEach(stream -> {
                        stream.addToValidList(validPair);
                    });
                }
                return;
            }
        }
        if (strategy == NominationStrategy.NOMINATE_FIRST_VALID) {
            strategyNominateFirstValid(ev);
        } else if (strategy == NominationStrategy.NOMINATE_HIGHEST_PRIO) {
            strategyNominateHighestPrio(ev);
        } else if (strategy == NominationStrategy.NOMINATE_FIRST_HOST_OR_REFLEXIVE_VALID) {
            strategyNominateFirstHostOrReflexiveValid(ev);
        }
    }

    /**
     * Implements a basic nomination strategy that consists in nominating the
     * first pair that has become valid for a check list.
     *
     * @param evt the {@link PropertyChangeEvent} containing the pair which
     * has been validated.
     */
    private void strategyNominateFirstValid(PropertyChangeEvent evt) {
        if (IceMediaStream.PROPERTY_PAIR_VALIDATED.equals(evt.getPropertyName())) {
            CandidatePair validPair = (CandidatePair) evt.getSource();
            logger.debug("Nominate (first valid): " + validPair.toShortString() + ". Local ufrag " + parentAgent.getLocalUfrag());
            parentAgent.nominate(validPair);
        }
    }

    /**
     * Implements a nomination strategy that allows checks for several (or all)
     * pairs in a check list to conclude before nominating the one with the
     * highest priority.
     *
     * @param ev the {@link PropertyChangeEvent} containing the new state and
     * the source {@link CheckList}.
     */
    private void strategyNominateHighestPrio(PropertyChangeEvent ev) {
        String pname = ev.getPropertyName();
        if (IceMediaStream.PROPERTY_PAIR_VALIDATED.equals(pname)
                || (IceMediaStream.PROPERTY_PAIR_STATE_CHANGED.equals(pname) && (ev.getNewValue() == CandidatePairState.FAILED))) {
            CandidatePair validPair = (CandidatePair) ev.getSource();
            Component parentComponent = validPair.getParentComponent();
            IceMediaStream parentStream = parentComponent.getParentStream();
            CheckList parentCheckList = parentStream.getCheckList();
            if (parentCheckList.allChecksCompleted()) {
                for (Component component : parentStream.getComponents()) {
                    CandidatePair pair = parentStream.getValidPair(component);
                    if (pair != null) {
                        logger.debug("Nominate (highest priority): " + validPair.toShortString());
                        parentAgent.nominate(pair);
                    }
                }
            }
        }
    }

    /**
     * Implements a nomination strategy that consists in nominating directly
     * host or server reflexive pair that has become valid for a
     * check list. For relayed pair, a timer is armed to see if no other host or
     * server reflexive pair gets validated prior to timeout, the relayed ones
     * gets nominated.
     *
     * @param evt the {@link PropertyChangeEvent} containing the pair which
     * has been validated.
     */
    private void strategyNominateFirstHostOrReflexiveValid(PropertyChangeEvent evt) {
        if (IceMediaStream.PROPERTY_PAIR_VALIDATED.equals(evt.getPropertyName())) {
            CandidatePair validPair = (CandidatePair) evt.getSource();
            Component component = validPair.getParentComponent();
            LocalCandidate localCandidate = validPair.getLocalCandidate();
            boolean isRelayed = (localCandidate instanceof RelayedCandidate)
                    || localCandidate.getType().equals(CandidateType.RELAYED_CANDIDATE)
                    || validPair.getRemoteCandidate().getType().equals(CandidateType.RELAYED_CANDIDATE);
            boolean nominate = false;
            TimerTask task = validatedCandidates.get(component.toShortString());
            if (isRelayed && task == null) {
                // armed a timer and see if a host or server reflexive pair gets nominated. Otherwise nominate the relayed candidate pair
                Timer timer = new Timer();
                task = new RelayedCandidateTask(validPair);
                logger.debug("Wait timeout to nominate relayed candidate");
                timer.schedule(task, 0);
                validatedCandidates.put(component.toShortString(), task);
            } else if (!isRelayed) {
                // host or server reflexive candidate pair
                if (task != null) {
                    task.cancel();
                    logger.debug("Found a better candidate pair to nominate for {}", component.toShortString());
                }
                logger.debug("Nominate (first highest valid): {}", validPair.toShortString());
                nominate = true;
            }
            if (nominate) {
                parentAgent.nominate(validPair);
            }
        }
    }

    /**
     * The {@link NominationStrategy} that this nominator should use when deciding whether or not a valid {@link CandidatePair} is suitable for
     * nomination.
     *
     * @param strategy the NominationStrategy we should be using
     */
    public void setStrategy(NominationStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * TimerTask that will wait a certain amount of time to let other candidate pair to be validated and possibly be better than the relayed candidate.
     */
    private class RelayedCandidateTask extends TimerTask implements PropertyChangeListener {
        /**
         * Wait time in milliseconds.
         */
        private static final int WAIT_TIME = 800;

        /**
         * The relayed candidate pair.
         */
        private final CandidatePair pair;

        /**
         * If the task has been cancelled.
         */
        private boolean cancelled;

        /**
         * Constructor.
         *
         * @param pair relayed candidate pair
         */
        public RelayedCandidateTask(CandidatePair pair) {
            this.pair = pair;
            pair.getParentComponent().getParentStream().getCheckList().addChecksListener(this);
        }

        /**
         * Tracks end of checks of the {@link CheckList}.
         *
         * @param evt the event
         */
        public void propertyChange(PropertyChangeEvent evt) {
            // Make it clear that PROPERTY_CHECK_LIST_CHECKS is in use here.
            if (!CheckList.PROPERTY_CHECK_LIST_CHECKS.equals(evt.getPropertyName())) {
                return;
            }
            // check list has run out of ordinary checks, see if all other candidates are FAILED, in which case we nominate immediately
            // the relayed candidate
            CheckList checkList = (CheckList) evt.getSource();
            boolean allFailed = true;
            for (CandidatePair c : checkList) {
                if (c != pair && c.getState() != CandidatePairState.FAILED) {
                    allFailed = false;
                    break;
                }
            }
            if (allFailed && !pair.isNominated()) {
                // all other pairs are failed to do not waste time, cancel timer and nominate ourself (the relayed candidate).
                this.cancel();
                logger.debug("Nominate (first highest valid): {}", pair.toShortString());
                parentAgent.nominate(pair);
            }
        }

        /**
         * Cancel task.
         */
        @Override
        public boolean cancel() {
            cancelled = true;
            return super.cancel();
        }

        /**
         * Task entry point.
         */
        public void run() {
            try {
                logger.debug("Going to sleep for 800ms for pair: {}", pair.toShortString());
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException e) {
                cancelled = true;
            }
            Component component = pair.getParentComponent();
            component.getParentStream().getCheckList().removeChecksListener(this);
            validatedCandidates.remove(component.toShortString());
            if (cancelled) {
                return;
            }
            logger.debug("Nominate (first highest valid): {}", pair.toShortString());
            // task has not been cancelled after WAIT_TIME milliseconds so nominate the pair
            parentAgent.nominate(pair);
        }
    }
}
