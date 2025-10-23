


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
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

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
//public class j4ActionMarkovAtomic extends AtomicModelImpl implements PhaseBased,
//this is action node
public class j4 extends AtomicModelImpl implements PhaseBased,
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
	private String job;
	protected Queue<String> queue;
	protected Queue<Double> time_queue;
	//ENDID
	String phase = "passive";
	String previousPhase = null;
	Double sigma = maxTA;
	Double previousSigma = Double.NaN;

	protected int job_received, job_dispatched, total_jobs_lost;
	protected double start_waiting, total_job_waiting_time, arrival_rate, throughput, job_arrived;
	FileWriter analyticsFile;
	public String folder = System.getProperty("user.dir") + File.separator;
	public String foldertxt = folder + "src\\Models\\csv\\"+ File.separator;
	public String resultsFileName = foldertxt+"j4.csv";
	// End state variables

	// Input ports

	public final Port<Serializable> inF_95 = 
			addInputPort("inF_95", Serializable.class);
	
	// End input ports

	// Output ports

	public final Port<Serializable> outFLOW16 = 
			addOutputPort("outFLOW16", Serializable.class);

	//ENDID

	// End output ports
	protected SimulationOptionsImpl options = new SimulationOptionsImpl();
	protected double currentTime;

	// This variable is just here so we can use @SuppressWarnings("unused")
	private final int unusedIntVariableForWarnings = 0;

	public j4() {
		this("?j4");
	}

	public j4(String name) {
		this(name, null);
	}

	public j4(String name, Simulator simulator) {
		super(name, simulator);
	}

	public void initialize() {
		super.initialize();

		currentTime = 0;

		// Default state variable initialization
		ctm = new ContinuousTimeMarkov();
		maxTA = Double.MAX_VALUE;

		int numberOfOutputPorts = 0;
			numberOfOutputPorts++;

		holdIn("passive", maxTA);

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
					File.separator + "j4.xml");

			TransitionInfo ti;
			TimeInfo tf;

			ti = ctm.getTransitionInfoFor("active", "portFLOW16_state");
            tf = ti.getTInfo();
            //ti.setProbValue(1.0/numberOfOutputPorts);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		holdIn("passive", maxTA);
		queue = new LinkedList<>();
		time_queue = new LinkedList<>();
		job_dispatched = job_received = 0;//added
		total_jobs_lost = 0;
		arrival_rate = throughput = 0.0;
		try {
			Files.createDirectories(Paths.get(foldertxt+""));
			analyticsFile = new FileWriter(resultsFileName);
			analyticsFile.write("Simulation time, Job arrival rate, Throughput, Total jobs lost\n");
			analyticsFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//<CTM>

		//ENDID
		// End initialize variables
	}

	@Override
	public void internalTransition() {
		currentTime += sigma;

		if (phaseIs("active")) {
			getSimulator().modelMessage("Internal transition from active");

			//ID:TRA:active
			holdIn("port2_state", maxTA);
			//ENDID
			// Internal event code
			//ID:INT:active

			//<CTM>
			internalTransitionForMarkov("active", maxTA, "");

			//<CTM>
			//ENDID
			// End internal event code
			return;
		}
		
		int counter = 0;
			counter++;
		
		if (phaseIs("portFLOW16_state")) {
			getSimulator().modelMessage("Internal transition from portFLOW16_state");

			//ID
			holdIn("passive", maxTA);
			appendStrToFile(resultsFileName, ""+currentTime+","+arrival_rate+","+throughput+","+total_jobs_lost+"\n");
			job_dispatched++;
			if(currentTime!=0)
				throughput = job_dispatched/currentTime;
			//ENDID

			return;
		}

	}

	@Override
	public void externalTransition(double timeElapsed, MessageBag x) {
		currentTime += timeElapsed;
		// Subtract time remaining until next internal transition (no effect if sigma == Infinity)
		sigma -= timeElapsed;

		// Store prior data
		
		if (phaseIs("passive")) {
			for (Port in_port : this.getInputPorts())
				if (x.hasMessages(in_port)) {
					ArrayList<Message<Serializable>> messageList = in_port.getMessages(x);

					for (int i = 0; i < messageList.size(); i++) {
						job = (String) messageList.get(i).getData(); //this.getName();//
						holdIn("active", 0.);
						job_received++;
						if(currentTime>0)
							arrival_rate = job_received/currentTime;
						appendStrToFile(resultsFileName, ""+currentTime+","+arrival_rate+","+throughput+","+total_jobs_lost+"\n");
					}
				}
		}
		else {
			total_jobs_lost++;
			job_received++;
			if(currentTime>0)
				arrival_rate = job_received/currentTime;
		}

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

		if (phaseIs("active")) {

			// Output event code
			//ID:OUT:active
			//<CTM>

			int counter = 0;
			counter++;

			if (ctm.isOutput() && ctm.getNextState().equals("portFLOW16_state")) {
				//output.add(FLOW16,"FLOW16");
				output.add(outFLOW16,job); //Modified to preserve I/O identity

			}

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
		//j4ActionMarkovAtomic model = new j4ActionMarkovAtomic();
		j4 model = new j4();
		model.options = options;

		if (options.isDisableViewer()) { // Command line output only
			Simulation sim =
					new com.ms4systems.devs.core.simulation.impl.SimulationImpl("j4 Simulation",
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
			//dirUri = j4ActionMarkovAtomic.class.getResource(".").toURI();
			dirUri = j4.class.getResource(".").toURI();
			dir = new File(dirUri);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			throw new RuntimeException(
					"Could not find Models directory. Invalid model URL: " +
							//j4ActionMarkovAtomic.class.getResource(".").toString());
							j4.class.getResource(".").toString());
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

		int counter = 0;
			
		return new String[] { "active"		,"portFLOW16_state"
 };
	}

	public void appendStrToFile(String fileName, String str) {
		try {
			// Open given file in append mode
			BufferedWriter out = new BufferedWriter(
					new FileWriter(fileName, true));
			out.write(str);
			out.close();
		}
		catch (IOException e) {
			System.out.println("exception occurred" + e);
		}
	}
}
