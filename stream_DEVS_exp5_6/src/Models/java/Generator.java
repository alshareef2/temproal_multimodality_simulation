
/* Do not remove or modify this comment!  It is required for file identification!
DNL
platform:/resource/HipFractureMarkov/src/Models/dnl/activity0_Generator.dnl
772451835
 Do not remove or modify this comment!  It is required for file identification! */
package Models.java;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import java.io.File;
import java.io.Serializable;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.*;
import java.util.ArrayList;

import com.ms4systems.devs.analytics.PMFForSurvival;
import com.ms4systems.devs.core.message.Message;
import com.ms4systems.devs.core.message.MessageBag;
import com.ms4systems.devs.core.message.Port;
import com.ms4systems.devs.core.message.impl.MessageBagImpl;
import com.ms4systems.devs.core.model.impl.AtomicModelImpl;
import com.ms4systems.devs.core.simulation.Simulation;
import com.ms4systems.devs.core.simulation.Simulator;
import com.ms4systems.devs.extensions.PhaseBased;
import com.ms4systems.devs.extensions.StateVariableBased;
import com.ms4systems.devs.helpers.impl.SimulationOptionsImpl;

// Custom library code
//ID:LIB:0
//<CTM>
import com.ms4systems.devs.markov.*;
import com.ms4systems.devs.simviewer.standalone.SimViewer;

//<CTM>

//ENDID
// End custom library code
@SuppressWarnings("unused")
//public class ACTIVITY1_MarkovGenerator extends AtomicModelImpl implements PhaseBased,
public class Generator extends AtomicModelImpl implements PhaseBased,
    StateVariableBased {
    private static final long serialVersionUID = 1L;

    //ID:SVAR:0
    private static final int ID_CTM = 0;

    //ENDID
    //ID:SVAR:1
    private static final int ID_MAXTA = 1;

    // Declare state variables
    private PropertyChangeSupport propertyChangeSupport =
        new PropertyChangeSupport(this);
    protected ContinuousTimeMarkov ctm = new ContinuousTimeMarkov();
    protected double maxTA = Double.MAX_VALUE;

    //ENDID
    String phase = "state2";
    String previousPhase = null;
    Double sigma = maxTA;
    Double previousSigma = Double.NaN;

    // End state variables

	int generated_output_counter;

    // Input ports
    // End input ports

    // Output ports
		public final Port<Serializable> outM1 = 
			addOutputPort("outM1", Serializable.class);
		public final Port<Serializable> outM2 = 
			addOutputPort("outM2", Serializable.class);
		public final Port<Serializable> outM3 = 
			addOutputPort("outM3", Serializable.class);
		public final Port<Serializable> outM4 = 
			addOutputPort("outM4", Serializable.class);
    //ENDID

    // End output ports
    protected SimulationOptionsImpl options = new SimulationOptionsImpl();
    protected double currentTime;

    // This variable is just here so we can use @SuppressWarnings("unused")
    private final int unusedIntVariableForWarnings = 0;

//    public ACTIVITY1_MarkovGenerator() {
    public Generator() {
        this("activity0_Generator");
    }

    public Generator(String name) {
        this(name, null);
    }

    public Generator(String name, Simulator simulator) {
        super(name, simulator);
    }

	public void initialize() {
		super.initialize();

		currentTime = 0;

		generated_output_counter = 1;
		ContinuousTimeMarkov.Seed = 2349991+19;

		// Default state variable initialization
		ctm = new ContinuousTimeMarkov();
		maxTA = Double.MAX_VALUE;

		int numberOfOutputPorts = 0;
	
			numberOfOutputPorts++;
			numberOfOutputPorts++;
			numberOfOutputPorts++;
			numberOfOutputPorts++;

		holdIn("generating", maxTA);

		// Initialize Variables
		//ID:INIT
		//<CTM>


		ctm = new ContinuousTimeMarkov();
		ctm.setTimeToNextEvent(0.0);
		ctm.setOutput(false);
		String path = getModelsDirectory().getAbsolutePath();
		path.replace("bin", "src");
		try {
			ctm.fillTransitionInfoList(path + File.separator + "xml" + 
					//File.separator + "ACTIVITY1_MarkovGenerator.xml");
					File.separator + "Generator.xml");

//modify TI

			TransitionInfo ti;
			TimeInfo tf;

			ti = ctm.getTransitionInfoFor("generating", "m1_state");
            tf = ti.getTInfo();
            //ti.setProbValue(1.0/numberOfOutputPorts);
			//tf.setLower(1000);
            //tf.setUpper(1000);
			ti = ctm.getTransitionInfoFor("generating", "m2_state");
            tf = ti.getTInfo();
            //ti.setProbValue(1.0/numberOfOutputPorts);
			//tf.setLower(1000);
            //tf.setUpper(1000);
			ti = ctm.getTransitionInfoFor("generating", "m3_state");
            tf = ti.getTInfo();
            //ti.setProbValue(1.0/numberOfOutputPorts);
			//tf.setLower(1000);
            //tf.setUpper(1000);
			ti = ctm.getTransitionInfoFor("generating", "m4_state");
            tf = ti.getTInfo();
            //ti.setProbValue(1.0/numberOfOutputPorts);
			//tf.setLower(1000);
            //tf.setUpper(1000);



		} catch (Exception e) {
			e.printStackTrace();
		}
		holdIn("generating", 0.);


		//<CTM>

		//ENDID
		// End initialize variables
	}

    @Override
    public void internalTransition() {
		currentTime += sigma;

		if (phaseIs("generating")) {
			getSimulator().modelMessage("Internal transition from generating");

			//ID:TRA:generating
			holdIn("m1_state", maxTA);

			holdIn("m2_state", maxTA);

			holdIn("m3_state", maxTA);












			holdIn("m4_state", maxTA);







			//ENDID
			// Internal event code
			//ID:INT:generating

			//<CTM>
			internalTransitionForMarkov("generating", maxTA, "");

			//<CTM>
			//ENDID
			// End internal event code
			return;
		}

		if (phaseIs("m1_state")) {
			getSimulator().modelMessage("Internal transition from port1_state");

			//ID:TRA:port1_state
			holdIn("generating", 0.);
			generated_output_counter++;
			//ENDID

			return;
		}
		if (phaseIs("m2_state")) {
			getSimulator().modelMessage("Internal transition from port1_state");

			//ID:TRA:port1_state
			holdIn("generating", 0.);
			generated_output_counter++;
			//ENDID

			return;
		}
		if (phaseIs("m3_state")) {
			getSimulator().modelMessage("Internal transition from port1_state");

			//ID:TRA:port1_state
			holdIn("generating", 0.);
			generated_output_counter++;
			//ENDID

			return;
		}
		if (phaseIs("m4_state")) {
			getSimulator().modelMessage("Internal transition from port1_state");

			//ID:TRA:port1_state
			holdIn("generating", 0.);
			generated_output_counter++;
			//ENDID

			return;
		}

		//passivate();
    }

    @Override
    public void externalTransition(double timeElapsed, MessageBag input) {
        currentTime += timeElapsed;
        // Subtract time remaining until next internal transition (no effect if sigma == Infinity)
        sigma -= timeElapsed;

        // Store prior data
        previousPhase = phase;
        previousSigma = sigma;

        // Fire state transition functions
    }

    @Override
    public void confluentTransition(MessageBag input) {
        // confluentTransition with internalTransition first (by default)
        internalTransition();
        externalTransition(0, input);
    }

    @Override
    public Double getTimeAdvance() {
        return sigma;
    }

    @Override
    public MessageBag getOutput() {
        MessageBag output = new MessageBagImpl();

        //System.out.println("Number of Generated Inputs: "+ generated_output_counter);

        if (phaseIs("generating")) {

            // Output event code
            //ID:OUT:state2

            //<CTM>

		if (ctm.isOutput() && ctm.getNextState().equals("m1_state")) {
			//output.add(outM1,"outM1");
			output.add(outM1, generated_output_counter+""); // Modified to preserve I/O identity
		}

		if (ctm.isOutput() && ctm.getNextState().equals("m2_state")) {
			//output.add(outM2,"outM2");
			output.add(outM2, generated_output_counter+""); // Modified to preserve I/O identity
		}

		if (ctm.isOutput() && ctm.getNextState().equals("m3_state")) {
			//output.add(outM3,"outM3");
			output.add(outM3, generated_output_counter+""); // Modified to preserve I/O identity
		}












		if (ctm.isOutput() && ctm.getNextState().equals("m4_state")) {
			//output.add(outM4,"outM4");
			output.add(outM4, generated_output_counter+""); // Modified to preserve I/O identity
		}








            //<CTM>

            //
            //
            ////Add your own code
            //output.add(outYes,null);
            //ENDID
            // End output event code
        }
        return output;
    }

    // Custom function definitions

    //ID:CUST:0
    //<CTM>
    public double[] fillProbabilities(String state) {
        if (ctm.getSuccs(state).size() == 0) {
            return new double[0];
        }
        double[] probabilities = new double[ctm.getSuccs(state).size()];
        for (String succ : ctm.getSuccs(state)) {
            if (succ.equals(state)) {
                continue;
            }
            com.ms4systems.devs.markov.TransitionInfo ti =
                ctm.getTransitionInfoFor(state, succ);
            probabilities[ctm.getIndex(succ)] = ti.getProbValue();
        }
        return probabilities;
    }

    public void internalTransitionForMarkov(String state, double sta,
        String phase) {
        double timeToNextEvent = ctm.getTimeToNextEvent();
        if (timeToNextEvent == 0) {
            timeToNextEvent = Double.POSITIVE_INFINITY;
            if (ctm.getSuccs(state).size() == 0) {
                return;
            }
            if (ctm.getSuccs(state).size() == 1) {
                ArrayList succs = ctm.getSuccs(state);
                if (succs.get(0).equals(state)) {
                    return;
                }
            }
            double sample = ctm.getRand().nextDouble();
            double min = 0.0;
            double max = 0.0;
            boolean found = false;
            String SelSucc = state;
            double norm = ctm.normalFactor(state);
            for (String succ : ctm.getSuccs(state)) {
                min = max;
                com.ms4systems.devs.markov.TransitionInfo ti =
                    ctm.getTransitionInfoFor(state, succ);
                max += ti.getProbValue() / norm;
                if (min < sample && sample <= max) {
                    found = true;
                    ctm.setNextState(succ);
                    SelSucc = succ;
                    break;
                }
            }
            if (!found) {
                ctm.setNextState(state);
            }
            double time = ctm.timeToNextEvent(state, SelSucc, norm);
            ctm.setTimeToNextEvent(time);
            timeToNextEvent = time;
            if (timeToNextEvent > sta) {
                timeToNextEvent = sta;
                ctm.setTimeToNextEvent(timeToNextEvent);
                if (!phase.equals("")) {
                    ctm.setNextState(phase);
                } else {
                    ctm.setNextState(state);
                }
            }
        }
        if (previousPhase != null && previousPhase.equals(state)) {
            previousPhase = null;
            ctm.setTimeToNextEvent(0.0);
            String nextState = ctm.getNextState();
            holdIn(nextState, 0.);
            ctm.setOutput(false);
        } else {
            com.ms4systems.devs.markov.TimeInState tm =
                ctm.getTimeInState(state);
            if (tm == null) {
                tm = new com.ms4systems.devs.markov.TimeInState();
                tm.setStateName(state);
                tm.setCountInState(0);
                tm.setElapsedTime(0.);
                ctm.getTimeInStateList().add(tm);
            }
            holdIn(state, timeToNextEvent);
            ctm.setOutput(true);
            previousPhase = state;
            ctm.incCount(tm);
            ctm.updateElapsedTime(tm, timeToNextEvent);
            double accLifeTime = ctm.getAccLifeTime() + timeToNextEvent;
            ctm.setAccLifeTime(accLifeTime);
            ctm.printTimeInState();
        }
    }

    //<CTM>

    //ENDID

    // End custom function definitions
    public static void main(String[] args) {
        SimulationOptionsImpl options = new SimulationOptionsImpl(args, true);

        // Uncomment the following line to disable SimViewer for this model
        // options.setDisableViewer(true);

        // Uncomment the following line to disable plotting for this model
        // options.setDisablePlotting(true);
        //activity0_Generator model = new activity0_Generator();
		//ACTIVITY1_MarkovGenerator model = new ACTIVITY1_MarkovGenerator();
		Generator model = new Generator();
        model.options = options;

        if (options.isDisableViewer()) { // Command line output only
            Simulation sim =
                new com.ms4systems.devs.core.simulation.impl.SimulationImpl("ACTIVITY1 Generator Simulation",
                    model, options);
            sim.startSimulation(0);
            sim.simulateIterations(Long.MAX_VALUE);
        } else { // Use SimViewer
            SimViewer viewer = new SimViewer();
            viewer.open(model, options);
        }
    }

    public void addPropertyChangeListener(String propertyName,
        PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    // Getter/setter for ctm
    public void setCtm(ContinuousTimeMarkov ctm) {
        propertyChangeSupport.firePropertyChange("ctm", this.ctm, this.ctm = ctm);
    }

    public ContinuousTimeMarkov getCtm() {
        return this.ctm;
    }

    // End getter/setter for ctm

    // Getter/setter for maxTA
    public void setMaxTA(double maxTA) {
        propertyChangeSupport.firePropertyChange("maxTA", this.maxTA,
            this.maxTA = maxTA);
    }

    public double getMaxTA() {
        return this.maxTA;
    }

    // End getter/setter for maxTA

    // State variables
    public String[] getStateVariableNames() {
        return new String[] { "ctm", "maxTA" };
    }

    public Object[] getStateVariableValues() {
        return new Object[] { ctm, maxTA };
    }

    public Class<?>[] getStateVariableTypes() {
        return new Class<?>[] { ContinuousTimeMarkov.class, Double.class };
    }

    public void setStateVariableValue(int index, Object value) {
        switch (index) {

            case ID_CTM:
                setCtm((ContinuousTimeMarkov) value);
                return;

            case ID_MAXTA:
                setMaxTA((Double) value);
                return;

            default:
                return;
        }
    }

    // Convenience functions
    protected void passivate() {
        passivateIn("passive");
    }

    protected void passivateIn(String phase) {
        holdIn(phase, Double.POSITIVE_INFINITY);
    }

    protected void holdIn(String phase, Double sigma) {
        this.phase = phase;
        this.sigma = sigma;
        getSimulator()
            .modelMessage("Holding in phase " + phase + " for time " + sigma);
    }

    protected static File getModelsDirectory() {
        URI dirUri;
        File dir;
        try {
            //dirUri = ACTIVITY1_MarkovGenerator.class.getResource(".").toURI();
            dirUri = Generator.class.getResource(".").toURI();
            dir = new File(dirUri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(
                "Could not find Models directory. Invalid model URL: " +
                //ACTIVITY1_MarkovGenerator.class.getResource(".").toString());
                Generator.class.getResource(".").toString());
        }
        boolean foundModels = false;
        while (dir != null && dir.getParentFile() != null) {
            if (dir.getName().equalsIgnoreCase("java") &&
                  dir.getParentFile().getName().equalsIgnoreCase("models")) {
                return dir.getParentFile();
            }
            dir = dir.getParentFile();
        }
        throw new RuntimeException(
            "Could not find Models directory from model path: " +
            dirUri.toASCIIString());
    }

    protected static File getDataFile(String fileName) {
        return getDataFile(fileName, "txt");
    }

    protected static File getDataFile(String fileName, String directoryName) {
        File modelDir = getModelsDirectory();
        File dir = new File(modelDir, directoryName);
        if (dir == null) {
            throw new RuntimeException("Could not find '" + directoryName +
                "' directory from model path: " + modelDir.getAbsolutePath());
        }
        File dataFile = new File(dir, fileName);
        if (dataFile == null) {
            throw new RuntimeException("Could not find '" + fileName +
                "' file in directory: " + dir.getAbsolutePath());
        }
        return dataFile;
    }

    protected void msg(String msg) {
        getSimulator().modelMessage(msg);
    }

    // Phase display
    public boolean phaseIs(String phase) {
        return this.phase.equals(phase);
    }

    public String getPhase() {
        return phase;
    }

    public String[] getPhaseNames() {
        return new String[] {
		"m1_state",

		"m2_state",

		"m3_state",












		"m4_state",







		};
    }
}

