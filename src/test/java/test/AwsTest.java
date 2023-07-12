/* See LICENSE.md for license information */
package test;

import java.util.*;
import java.util.logging.*;

import com.red5pro.ice.ice.*;
import com.red5pro.ice.ice.harvest.*;

/**
 * An example of using the AWS Harvester.
 * <p>
 * @author Emil Ivov
 */
public class AwsTest extends Ice {

    private static final Logger logger = Logger.getLogger(AwsTest.class.getName());

    /**
     * Runs a test application that creates an agent, attaches an AWS harvester
     * as well as a few allocates streams, generates an SDP and dumps
     * it on stdout
     *
     * @param args none currently handled
     * @throws Throwable every now and then.
     */
    public static void main(String[] args) throws Throwable {
        if (!AwsCandidateHarvester.smellsLikeAnEC2()) {
            logger.info("This does not appear to be an EC2 machine");
            return;
        } else {
            logger.info("Oh nice! Looks like we are on an EC2 machine");

        }
        AwsCandidateHarvester mch = new AwsCandidateHarvester();

        List<CandidateHarvester> harvesters = new ArrayList<>();
        harvesters.add(mch);

        Agent localAgent = createAgent(2020, false, harvesters);
        localAgent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);
        // create a resource file for a test sdp
        //String localSDP = SdpUtils.createSDPDescription(localAgent);

        //wait a bit so that the logger can stop dumping stuff:
        Thread.sleep(500);

        logger.info("=================== feed the following" + " to the remote agent ===================");
        //logger.info("\n" + localSDP);
        logger.info("======================================" + "========================================\n");
    }
}
