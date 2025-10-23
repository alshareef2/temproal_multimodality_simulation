package Models.java;
import java.io.Serializable;

import com.ms4systems.devs.core.model.impl.CoupledModelImpl;
import com.ms4systems.devs.core.simulation.Simulation;
import com.ms4systems.devs.core.message.Port;
import com.ms4systems.devs.helpers.impl.SimulationOptionsImpl;
import com.ms4systems.devs.simviewer.standalone.SimViewer;


public class EFACTIVITY1 extends CoupledModelImpl{

	private static final long serialVersionUID = 1L;
	protected SimulationOptionsImpl options = new SimulationOptionsImpl();
	public final Port<Serializable> outPORT = 
		addOutputPort("outPORT", Serializable.class);

	public EFACTIVITY1(String name) {
		super(name);
	}

	public EFACTIVITY1() {
		super("EF");
		
		//ACTIVITY1_Generator g = new ACTIVITY1_Generator("Generator", 20.0, 1);
		//ACTIVITY1_MarkovGenerator g = new ACTIVITY1_MarkovGenerator("Generator");
		Generator g = new Generator("Generator");

		ACTIVITY1 a = new ACTIVITY1("ACTIVITY1",10);
		
		addChildModel(g);
		addChildModel(a);
		
		addCoupling(g.outM1,a.inM1);
		addCoupling(g.outM2,a.inM2);
		addCoupling(g.outM3,a.inM3);
		addCoupling(a.outY,this.outPORT);
		addCoupling(g.outM4,a.inM4);

		Transducer t = new Transducer("Transducer");
		addChildModel(t);

		addCoupling(g.outM1,t.inGeneratedJobs);
		addCoupling(g.outM2,t.inGeneratedJobs);
		addCoupling(g.outM3,t.inGeneratedJobs);
		addCoupling(a.outY,t.inProcessedJobs);
		addCoupling(g.outM4,t.inGeneratedJobs);
	}

	public static void main(String[] args){
		SimulationOptionsImpl options = new SimulationOptionsImpl(args, true);
		EFACTIVITY1 model = new EFACTIVITY1();
		long iterations = -1;
		model.options = options;
		options.setDisableViewer(true);
		options.setDisableLogging(true);
		if(options.isDisableViewer()){ // Command Line output only
			Simulation sim = new com.ms4systems.devs.core.simulation.impl.SimulationImpl("EFACTIVITY1 Simulation",model,options);
			sim.startSimulation(0);
			sim.setMaxSimulationTime(10000);
			if(iterations < 0 )
				sim.simulateIterations(Long.MAX_VALUE);
			else
				sim.simulateIterations(iterations);
		}else { //Use SimViewer
			SimViewer viewer = new SimViewer();
			viewer.open(model,options);
		}
	}
}
