package multipartyexample;

import java.lang.Math;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import negotiator.AgentID;
import negotiator.Bid;
import negotiator.Domain;
import negotiator.Deadline;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;
import negotiator.issue.Issue;
import negotiator.issue.IssueDiscrete;
import negotiator.issue.IssueInteger;
import negotiator.issue.IssueReal;
import negotiator.parties.AbstractNegotiationParty;
import negotiator.parties.NegotiationInfo;
import negotiator.persistent.PersistentDataContainer;
import negotiator.utility.AbstractUtilitySpace;
import negotiator.utility.UtilitySpace;
import negotiator.timeline.TimeLineInfo;

public class straygent extends AbstractNegotiationParty {

	private Bid lastReceivedBid = null;
    private Domain domain = null;
    
    private List<BidInfo> lBids;
    
    private double threshold_high = 1.0;
    private double threshold_low = 0.99;
    
    private final int TOLERANCE_LEVELS = 4;
    private List<Double> PSYCH_LEVELS;
    private double PSYCH = 0.0;
    
	@Override
	public void init(NegotiationInfo info) {
		
		super.init(info);
        this.domain = getUtilitySpace().getDomain();
        
        lBids = new ArrayList<>(Stray.generateRandomBids(this.domain, 30000,
				this.rand, this.utilitySpace));
				
		Collections.sort(lBids, Stray.byUtil);
	}

	@Override
	public Action chooseAction(List<Class<? extends Action>> validActions) {
        
//         System.out.printf(timeline.getTotalTime() + " _ " + timeline.getCurrentTime() + "\n");
    
        // Thresholds
        PSYCH_LEVELS = new ArrayList<>();
        PSYCH_LEVELS.add(Stray.gaussian(timeline.getTime(), 0.0, 0.1));
        PSYCH_LEVELS.add(Stray.gaussian(timeline.getTime(), 0.333, 0.3));
        PSYCH_LEVELS.add(Stray.gaussian(timeline.getTime(), 0.666, 0.2));
        PSYCH_LEVELS.add(Stray.gaussian(timeline.getTime(), 0.999, 0.3));
        
//         System.out.printf("Normalized: " + PSYCH_LEVELS + "\n");
        
        PSYCH_LEVELS = Stray.softmax(PSYCH_LEVELS);
        
//         System.out.printf("Softmaxed: " + PSYCH_LEVELS + "\n");
        
        for (int i = 1; i < TOLERANCE_LEVELS; i++){
            PSYCH_LEVELS.set(i, (PSYCH_LEVELS.get(i-1) + PSYCH_LEVELS.get(i)));
         }
        
//         System.out.printf("Weighted: " + PSYCH_LEVELS + "\n");
        
        PSYCH = rand.nextDouble();

        if (PSYCH <= PSYCH_LEVELS.get(0)) {
            threshold_high = 1;
            threshold_low = 1 - 0.05 * timeline.getTime();
//             System.out.printf("0: PSYCH: %.3f, TH: %.3f, TL: %.3f\n", PSYCH_LEVELS.get(0), threshold_high, threshold_low);
        } else if (PSYCH <= PSYCH_LEVELS.get(1)) {
            threshold_high = 1 - 0.05 * timeline.getTime();
            threshold_low = 1 - 0.1 * timeline.getTime();
//             System.out.printf("1: PSYCH: %.3f, TH: %.3f, TL: %.3f\n", PSYCH_LEVELS.get(1), threshold_high, threshold_low);
        } else if (PSYCH <= PSYCH_LEVELS.get(2)) {
            threshold_high = 1 - 0.1 * timeline.getTime();
            threshold_low = 1 - 0.15 * timeline.getTime();
//             System.out.printf("2: PSYCH: %.3f, TH: %.3f, TL: %.3f\n", PSYCH_LEVELS.get(2), threshold_high, threshold_low);
        } else {
            threshold_high = 1 - 0.15 * timeline.getTime();
            threshold_low = 1 - 0.2 * timeline.getTime();
//             System.out.printf("3: PSYCH: %.3f, TH: %.3f, TL: %.3f\n", PSYCH_LEVELS.get(3), threshold_high, threshold_low);
        } 
        
        // Accept
		if (lastReceivedBid != null) {
            for (BidInfo bi : this.lBids) {
//                 System.out.printf("Entered 1\n");
                if (bi.getBid().equals(lastReceivedBid)) {
//                     System.out.printf("Entered 2\n");
                    bi.setfreq(bi.getfreq() + 1);
//                     System.out.printf("Bid updated to: %d\n", bi.getfreq());
                    break;
                }
            }
			if (getUtility(lastReceivedBid) > threshold_low) {
				return new Accept(getPartyId(), lastReceivedBid);
			}
		}
		
		// Offer
		Bid bid = null;
		while (bid == null) {
			bid = Stray.selectBidfromList(this.lBids, this.threshold_high,
					this.threshold_low, timeline.getTime());
			if (bid == null) {
				threshold_low -= 0.0001;
			}
		}
		return new Offer(getPartyId(), bid);
	}

	@Override
	public void receiveMessage(AgentID sender, Action action) {
		super.receiveMessage(sender, action);
		if (action instanceof Offer) {
			lastReceivedBid = ((Offer) action).getBid();
		}
	}

	@Override
	public String getDescription() {
		return "Straygent";
	}

}

class Stray {

    private static Random random = new Random();
    
    public static Bid selectBidfromList(List<BidInfo> bidInfoList, double hiutil, double loutil, double timer) {
        List<BidInfo> bidInfos = new ArrayList<>();
        
        Collections.sort(bidInfoList, Stray.byFreq);

//         System.out.printf("List:\n");
//         for (int i = 0; i <= 9; i++) {
//             System.out.printf("%s %s\n", bidInfoList.get(i).getutil(), bidInfoList.get(i).getfreq());
//         }
        
        for (BidInfo bidInfo : bidInfoList) {
            if (bidInfo.getutil() <= hiutil && bidInfo.getutil() >= loutil) {
                bidInfos.add(bidInfo);
            }
        }
//         System.out.printf("Valid Bids: to select: %d\n", bidInfos.size());
        if (bidInfos.size() == 0) {
            return null;
        } else {
            return bidInfos.get(random.nextInt(bidInfos.size())).getBid();
        }
    }
    
    public static Set<BidInfo> generateRandomBids(Domain d, int numberOfBids, Random random, UtilitySpace utilitySpace) {
        Set<BidInfo> randombids = new HashSet<>();
        for (int i = 0; i < numberOfBids; i++) {
            Bid b = d.getRandomBid(random);
            randombids.add(new BidInfo(b, utilitySpace.getUtility(b), 0));
        }
        return randombids;
    }
    
    public static double gaussian(double x, double mu, double sig) {
        return Math.exp(-Math.pow(x - mu, 2.) / (2 * Math.pow(sig, 2.)));
    }
    
    public static List<Double> softmax(List<Double> lItems) {
        List<Double> exps = new ArrayList<>();
        double sums = 0.0;
        
        for (double i : lItems) {
            exps.add(Math.exp(i));
            sums += Math.exp(i);
        }
        List<Double> soft = new ArrayList<>();
        for (double i : exps) {
            soft.add(Math.ceil(i/sums*1000)/1000);
        }
        return soft;
    }
    
    static Comparator<BidInfo> byUtil = Comparator.comparing(
            BidInfo::getutil, (o1, o2) -> {
                return o2.compareTo(o1);
        });
        
    static Comparator<BidInfo> byFreq = Comparator.comparing(
            BidInfo::getfreq, (o1, o2) -> {
                return o2.compareTo(o1);
        });
}

class BidInfo {
	Bid bid;
	double util;
	int freq;

	public BidInfo(Bid b) {
		this.bid = b;
		util = 0.0;
		freq = 0;
	}

	public BidInfo(Bid b, double u, int f) {
		this.bid = b;
		util = u;
		freq = f;
	}
	
	public void setutil(double u) {
		util = u;
	}

	public void setfreq(int f) {
		freq = f;
	}

	public Bid getBid() {
		return bid;
	}

	public double getutil() {
		return util;
	}
	
	public int getfreq() {
		return freq;
	}
	
	public void updfreq() {
        freq += 1;
    }

	@Override
	public int hashCode() {
		return bid.hashCode();
	}

	public boolean equals(BidInfo bidInfo) {
		return bid.equals(bidInfo.getBid());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof BidInfo) {
			return ((BidInfo) obj).getBid().equals(bid);
		} else {
			return false;
		}
	}
}
