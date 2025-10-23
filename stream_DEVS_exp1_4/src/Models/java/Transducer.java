package Models.java;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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
public class Transducer extends AtomicModelImpl implements PhaseBased {

	private static final long serialVersionUID = 1L;

	//ENDID

	// End state variables
	String phase = "passive";
	Double sigma = 1.0;
	protected String job;
	protected Queue<Double> time_queue;
	protected Map<String,Double> map;
	protected int generated_job_counter, processed_job_counter;
	protected double arrival_rate, throughput, average_turnaround_time, total_turnaround_time;
	protected boolean arbitrary, exactMatch, FIFOMerge;
	FileWriter analyticsFile;
	public String folder = System.getProperty("user.dir") + File.separator;
	public String foldertxt = folder + "src\\Models\\csv\\"+ File.separator;
	public String resultsFileName = foldertxt+"ACTIVITY1.csv";

	// Input ports
	public final Port<Serializable> inGeneratedJobs = 
			addInputPort("inGeneratedJobs", Serializable.class);
	public final Port<Serializable> inProcessedJobs = 
			addInputPort("inProcessedJobs", Serializable.class);
	// End input ports

	// Output ports
	//ENDID

	// End output ports
	protected SimulationOptionsImpl options = new SimulationOptionsImpl();
	protected double currentTime;

	public Transducer() {
		this("Transdcuer");
	}

	public Transducer(String name) {
		super(name);
	}

	public void initialize() {
		super.initialize();
		time_queue = new LinkedList<>();
		map = new HashMap<>();
		currentTime = arrival_rate = throughput = average_turnaround_time = total_turnaround_time = 0.0;
		generated_job_counter = processed_job_counter =0;
		arbitrary = exactMatch = FIFOMerge = false;
		arbitrary = true;
		try {
			Files.createDirectories(Paths.get(foldertxt+""));
			analyticsFile = new FileWriter(resultsFileName);
			analyticsFile.write("Simulation time, Job produced counter, Turnaround time, Arrival time, Job arrival rate, Throughput, Queue size, Average turnaround time\n");
			analyticsFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void externalTransition(double e, MessageBag x) {
		currentTime += e;
		sigma -= e;

		for (int i=0; i< x.size();i++){
			for(Port in_port: this.getInputPorts())
				if (x.hasMessages(in_port)){
					ArrayList<Message<Serializable>> messageList = in_port.getMessages(x);
					for (int k = 0; k < messageList.size(); k++) {
						job = (String) messageList.get(k).getData();
						if(in_port.getName().equals("inGeneratedJobs")) {
							holdIn("expecting", Double.POSITIVE_INFINITY);
							time_queue.add(currentTime);
							map.put(job, currentTime);
							generated_job_counter++;
							if(currentTime>0)
								arrival_rate = generated_job_counter/currentTime;
						}
						else if(in_port.getName().equals("inProcessedJobs")) {
							processed_job_counter++;
							if(currentTime>0)
								throughput = processed_job_counter/currentTime;
							if(arbitrary && time_queue.size()>0){
								double arrival_time = time_queue.remove();
								double turnaround_time = currentTime-arrival_time;
								total_turnaround_time += turnaround_time;
								average_turnaround_time = total_turnaround_time/processed_job_counter;
								appendStrToFile(resultsFileName, ""+currentTime+","+processed_job_counter+","+turnaround_time+","+arrival_time+","+arrival_rate+","+throughput+","+time_queue.size()+","+average_turnaround_time+"\n");
							} else if (exactMatch && map.size()>0) {
								double arrival_time, turnaround_time;
								if(map.containsKey(job)) {
									arrival_time = map.get(job);
									turnaround_time = currentTime-arrival_time;
									total_turnaround_time += turnaround_time;
									average_turnaround_time = total_turnaround_time/processed_job_counter;
									appendStrToFile(resultsFileName, ""+currentTime+","+processed_job_counter+","+turnaround_time+","+arrival_time+","+arrival_rate+","+throughput+","+map.size()+","+average_turnaround_time+"\n");
								} else {
									arrival_time = currentTime;
									turnaround_time = 0.0;
									total_turnaround_time += turnaround_time;
									average_turnaround_time = total_turnaround_time/processed_job_counter;
									appendStrToFile(resultsFileName, ""+currentTime+","+processed_job_counter+","+turnaround_time+","+arrival_time+","+arrival_rate+","+throughput+","+map.size()+","+average_turnaround_time+"\n");
								}
							} else if (FIFOMerge && time_queue.size()>0) {
								double arrival_time = time_queue.remove();
								double turnaround_time = currentTime-arrival_time;
								total_turnaround_time += turnaround_time;
								average_turnaround_time = total_turnaround_time/processed_job_counter;
								appendStrToFile(resultsFileName, ""+currentTime+","+processed_job_counter+","+turnaround_time+","+arrival_time+","+arrival_rate+","+throughput+","+time_queue.size()+","+average_turnaround_time+"\n");	
							}
							else
								appendStrToFile(resultsFileName, ""+currentTime+","+processed_job_counter+","+"NA"+","+"NA"+","+arrival_rate+","+throughput+","+0+","+average_turnaround_time+"\n");
						}
					}
				}
		}
		if((arbitrary && time_queue.size()==0) || (exactMatch && map.size()==0) || (FIFOMerge && time_queue.size()==0))
			holdIn("passive",Double.POSITIVE_INFINITY);
	}

	@Override
	public void internalTransition()
	{
		currentTime += sigma;

		if((arbitrary && time_queue.size()>0) || (exactMatch && map.size()>0) || (FIFOMerge && time_queue.size()>0)) {
			holdIn("expecting",Double.POSITIVE_INFINITY);
		}
		else {
			holdIn("passive",Double.POSITIVE_INFINITY);
		}
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

	protected void holdIn(String phase, Double sigma) {
		this.phase = phase;
		this.sigma = sigma;
		getSimulator().modelMessage(
				"Holding in phase " + phase + " for time " + sigma);
	}

	@Override
	public String getPhase() {
		return phase;
	}

	@Override
	public String[] getPhaseNames() {
		return new String[] {
				"expecting","passive"
		};
	}

	@Override
	public boolean phaseIs(String arg0) {
		return this.phase.equals(arg0);
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
			System.out.println(e);
		}
	}
}
