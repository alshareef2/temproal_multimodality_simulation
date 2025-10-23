
package Models.java;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.ms4systems.devs.core.message.Message;
import com.ms4systems.devs.core.message.MessageBag;
import com.ms4systems.devs.core.message.Port;
import com.ms4systems.devs.core.message.impl.MessageBagImpl;
import com.ms4systems.devs.core.model.impl.AtomicModelImpl;
import com.ms4systems.devs.core.simulation.Simulator;
import com.ms4systems.devs.extensions.PhaseBased;
import com.ms4systems.devs.helpers.impl.SimulationOptionsImpl;

// this is a sync node
public class sync extends AtomicModelImpl implements PhaseBased{
	protected String job;
	protected List<String> jobs;
	protected double processing_time;
	protected List<String> out_ports;
	protected Map<String,Queue> in_ports;
	protected Map<String,LinkedList> in_ports_2;
	protected String firstPortName;
	protected int job_received, job_created;
	protected String job_created_str;
	
	String phase = "passive";
	Double sigma = 1.0;
	protected double currentTime;
	protected SimulationOptionsImpl options = new SimulationOptionsImpl();
	protected double total_waiting_time, start_waiting, total_job_waiting_time, arrival_rate, throughput;
	FileWriter analyticsFile;
	public String folder = System.getProperty("user.dir") + File.separator;
	public String foldertxt = folder + "src\\Models\\csv\\"+ File.separator;
	public String resultsFileName = foldertxt+"sync.csv";
	// Input ports

	public final Port<Serializable> inFLOW6 = 
			addInputPort("inFLOW6", Serializable.class);
	public final Port<Serializable> inFLOW7 = 
			addInputPort("inFLOW7", Serializable.class);
	public final Port<Serializable> inFLOW15 = 
			addInputPort("inFLOW15", Serializable.class);
	public final Port<Serializable> inFLOW17 = 
			addInputPort("inFLOW17", Serializable.class);
	
	// End input ports

	// Output ports

	public final Port<Serializable> outFLOW8 = 
			addOutputPort("outFLOW8", Serializable.class);
	public final Port<Serializable> outFLOW18 = 
			addOutputPort("outFLOW18", Serializable.class);

	//ENDID

	public sync(String  name,double  Processing_time){
		super(name);
		addInputPort("in");
		addOutputPort("out");
		in_ports = new HashMap<String,Queue>();
		out_ports = new ArrayList<String>();
		in_ports.put("in", new LinkedList());
		out_ports.add("out");
		processing_time = Processing_time;
		//addTestInput("in",new String("job"));
	}

	//public final Port<Serializable> inSync =
	// addInputPort("inSync", Serializable.class);

	public sync(String  name,double  Processing_time, 
			List<String> in_ports, List<String> out_ports){
		super(name);

		this.in_ports = new HashMap<String,Queue>();
		this.in_ports_2 = new HashMap<String,LinkedList>();

		for(String port_name: in_ports){
			//addInputPort(port_name, Serializable.class);
			this.in_ports.put(port_name, new LinkedList<String>());
			this.in_ports_2.put(port_name, new LinkedList<String>());
		}

		if(in_ports.size()>0)
			firstPortName = in_ports.get(0);

		this.out_ports = out_ports;
		//for(String port_name: out_ports)
			//addOutputPort(port_name, Serializable.class);
	}

	public sync() {
		this("Sync");
	}
	
	public sync(String name) {
		this(name, null);
	}
	
	public sync(String name, Simulator simulator) {
		super(name, simulator);
	}

	public void initialize(){
		phase = "passive";
		sigma = Double.POSITIVE_INFINITY;
		job = new String("job");
		job_created = job_received = 0;
		job_created_str = "";
		jobs = new LinkedList<String>();
		total_waiting_time = 0.0;
		total_job_waiting_time = 0.0;
		arrival_rate = throughput = 0.0;
		try {
			Files.createDirectories(Paths.get(foldertxt+""));
			analyticsFile = new FileWriter(resultsFileName);
			analyticsFile.write("Simulation time, Total waiting time, Total job waiting, Average queue size, Job arrival rate, Throughput, Job from queue, Job has waited for\n");
			analyticsFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		super.initialize();
	}

	@Override
	public void externalTransition(double e, MessageBag x) {
		currentTime += e;
		sigma -= e;

		for (int i=0; i< x.size();i++){
			//for(Map.Entry<String,Queue> entry: in_ports.entrySet())
			for(Port in_port: this.getInputPorts())
				if (x.hasMessages(in_port)){

					ArrayList<Message<Serializable>> messageList = in_port.getMessages(x);

					for (int k = 0; k < messageList.size(); k++) {
						job = (String) messageList.get(k).getData(); //this.getName();//
						//holdIn("active", 0.);
						in_ports.get(in_port.getName()).add(""+currentTime);//in_port.getMessages(x).get(0));
						in_ports_2.get(in_port.getName()).add(job);
						job_received++;
						if(currentTime>0)
							arrival_rate = job_received/currentTime;
					}
					if(in_ports.size()==1)
						start_waiting = currentTime;
					//in_ports.get(in_port.getName()).add(in_port.getMessages(x));//in_port.getMessages(x).get(0));
					//in_ports_2.get(in_port.getName()).add(in_port.getMessages(x));
				}
			if(allQueuesAreNonEmpty() && inputIsInAllQueues() && !phaseIs("combining")){
				//prepareOutput();
				total_waiting_time += currentTime - start_waiting;
				appendStrToFile(resultsFileName, ""+currentTime+","+total_waiting_time+","+total_job_waiting_time+","+averageQueueSize()+","+arrival_rate+","+throughput+",,\n");
				//System.out.println("Current total waiting time in node " +this.getName()+ " : " + total_waiting_time);
				job_created++;
				if(currentTime!=0)
					throughput = job_created/currentTime;
				holdIn("combining",processing_time);
			} else if(phaseIs("passive")){
				start_waiting = currentTime;
				holdIn("waiting",Double.POSITIVE_INFINITY);
			}
		}
	}

	@Override
	public void internalTransition()
	{
		currentTime += sigma;

		removeElementFromAllQueues();
		if(allQueuesAreNonEmpty()) {
			total_waiting_time += currentTime - start_waiting;
			//System.out.println("Current total waiting time in node " +this.getName()+ " : " + total_waiting_time);
			appendStrToFile(resultsFileName, ""+currentTime+","+total_waiting_time+","+total_job_waiting_time+","+averageQueueSize()+","+arrival_rate+","+throughput+",,\n");
			job_created++;
			holdIn("combining",processing_time);
		}
		else if(allQueuesAreEmpty())
			holdIn("passive",Double.POSITIVE_INFINITY);
		else {
			holdIn("waiting",Double.POSITIVE_INFINITY);
			start_waiting = currentTime;
		}
	}

	private boolean allQueuesAreNonEmpty() {
		for(Map.Entry<String,Queue> entry: in_ports.entrySet())
			if (entry.getValue().isEmpty())
				return false;

		return true;
	}

	private boolean inputIsInAllQueues() {
		//check all inputs of first queue
		if(in_ports_2.size()<1)
			return true;

		//LinkedList<String> firstQueue = new LinkedList<String>();
		//firstQueue = in_ports_2.get(firstPortName);
		//System.out.println("PEEK QUEUE="+ firstQueue.get(0).toString());

		for(Map.Entry<String,LinkedList> entry: in_ports_2.entrySet())
			if (!entry.getValue().isEmpty()) {
				boolean[] found = new boolean[in_ports_2.size()];
				for(int k=0; k < found.length; k++)
					found[k] = false;

				for(int i=0; i < entry.getValue().size(); i++) {
					//System.out.println("PEEK: "+entry.getValue().get(i));
					job = entry.getValue().get(i).toString();
					int counter = 0;
					for(Map.Entry<String,LinkedList> entry2: in_ports_2.entrySet())
					{
						if (!entry2.getValue().isEmpty()) {
							for(int j=0; j < entry2.getValue().size(); j++) {
								String str = entry2.getValue().get(j).toString();
								//if(job.equals(str)) {
								found[counter] = true;
								//}
							}
						}
						else
							return false;
						counter++;
					}
					boolean check = true;
					for(int k=0; k < found.length && check; k++)
						if(!found[k])
							check = false;
					if(check)
						return true;
				}
				return false;
			} else
				return false;

		return true;
	}

	private boolean inputIsInAllQueuesExactMatch() {
		//check all inputs of first queue
		if(in_ports_2.size()<1)
			return true;

		List<String> entry = in_ports_2.get(firstPortName);

		if (entry!=null && !entry.isEmpty()) {
			boolean[] found = new boolean[in_ports_2.size()];
			for(int k=0; k < found.length; k++)
				found[k] = false;
			for(int i=0; i < entry.size(); i++) {
				job = entry.get(i).toString();
				int counter = 0;
				for(Map.Entry<String,LinkedList> entry2: in_ports_2.entrySet())
				{
					if (!entry2.getValue().isEmpty()) {
						if(entry2.getValue().contains(job))
							found[counter] = true;
					}
					else
						return false;
					counter++;
				}
				boolean check = true;
				for(int k=0; k < found.length && check; k++)
					if(!found[k])
						check = false;
				if(check) {
					job_created_str = job;
					return true;
				}
			}
			return false;
		} else
			return false;

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


	private boolean allQueuesAreEmpty() {
		for(Map.Entry<String,Queue> entry: in_ports.entrySet())
			if (!entry.getValue().isEmpty())
				return false;
		return true;
	}

	private void prepareOutput() {
		jobs = new ArrayList<String>();
		for(Map.Entry<String,Queue> entry: in_ports.entrySet())
			jobs.add(entry.getValue().peek().toString());
	}

	private void removeElementFromAllQueues() {
		int count = 1;
		for(Map.Entry<String,Queue> entry: in_ports.entrySet()) {
			String t = (String) entry.getValue().remove();
			double wt = Double.parseDouble(t);
			//System.out.println("Job from queue " + count +" in node "+ this.getName() +" has waited : "+(currentTime-wt));
			appendStrToFile(resultsFileName, ""+currentTime+","+total_waiting_time+","+total_job_waiting_time+","+averageQueueSize()+","+arrival_rate+","+throughput+","+(count++)+","+(currentTime-wt)+"\n");
			total_job_waiting_time += currentTime-wt;
		}
		for(Map.Entry<String,LinkedList> entry: in_ports_2.entrySet())
			entry.getValue().remove();
	}

	private void removeElementFromAllQueuesExactMatch() {
		int count = 1;
		String to_be_removed = job_created_str;
		int atIndex;

		for(Map.Entry<String,LinkedList> entry: in_ports_2.entrySet()) {
			atIndex = entry.getValue().indexOf(to_be_removed);
			entry.getValue().remove(to_be_removed);
			double wt = removeAt(in_ports.get(entry.getKey()),atIndex);
			appendStrToFile(resultsFileName, ""+currentTime+","+total_waiting_time+","+total_job_waiting_time+","+averageQueueSize()+","+arrival_rate+","+throughput+","+(count++)+","+(currentTime-wt)+"\n");
			total_job_waiting_time += currentTime-wt;
		}

	}

	private double removeAt(Queue<String> queue, int index) {
		if (queue == null)
			return 0.0;

		int size = queue.size();
		if (index < 0 || size < index + 1)
			return 0.0;

		String element = null;
		for (int i = 0; i < size; i++) {
			if (i == index) {
				element = queue.remove();
			} else {
				queue.add(queue.remove());
			}
		}

		return Double.parseDouble(element);
	}

	private void mergeFirstElementFromAllQueues() {
		for(Map.Entry<String,Queue> entry: in_ports.entrySet()) {
			String t = (String) entry.getValue().remove();
			double wt = Double.parseDouble(t);
			//System.out.println("Job from queue " + count +" in node "+ this.getName() +" has waited : "+(currentTime-wt));
			total_job_waiting_time += currentTime-wt;
		}
		jobs.clear();
		for(Map.Entry<String,LinkedList> entry: in_ports_2.entrySet())
			jobs.add((String) entry.getValue().remove());
		job_created_str = putInOneLine(jobs);
	}

	private LinkedList<String> mergeInputs(String[] jobList) {
		LinkedList<String> uniqueList = new LinkedList<String>(); 
		for(String job: jobList)
			if(!uniqueList.contains(job))
				uniqueList.add(job);
		return uniqueList;
	}

	private String putInOneLine(List<String> list) {
		String str = "";
		for(String job: list)
			str += job + " ";
		return str;
	}
	
	private String firstLine(String str) {
		if(str.contains("\n"))
			return str.substring(0, str.indexOf("\n"));
		else
			return str;
	}

	private double averageQueueSize() {
		int sum = 0;
		for(Map.Entry<String,Queue> entry: in_ports.entrySet()){
			sum += entry.getValue().size();
		}
		return ((double) sum)/in_ports.size();
	}

	@Override
	public MessageBag getOutput()
	{
		MessageBag m = new MessageBagImpl();
		if (phaseIs("combining"))
			for(Port entry : this.getOutputPorts()) {
				//for(String j: jobs)
				//m.add(entry,j);
				m.add(entry,averageQueueSize()+"");
			}
		return m;
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
				"combining","waiting","passive"
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
			System.out.println("exception occurred" + e);
		}
	}
}

