package Models.java;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;

import com.ms4systems.devs.core.message.Port;
import com.ms4systems.devs.core.model.impl.CoupledModelImpl;
import com.ms4systems.devs.core.simulation.Simulation;
import com.ms4systems.devs.helpers.impl.SimulationOptionsImpl;
import com.ms4systems.devs.simviewer.standalone.SimViewer;


//public class ACTIVITY1Coupled extends CoupledModelImpl {
public class ACTIVITY1 extends CoupledModelImpl {
	private static final long serialVersionUID = 1L;
	protected SimulationOptionsImpl options = new SimulationOptionsImpl();
	final static int step = 0;

	public final Port<Serializable> inM1 = 
			addInputPort("inM1", Serializable.class);
	public final Port<Serializable> inM2 = 
			addInputPort("inM2", Serializable.class);
	public final Port<Serializable> inM3 = 
			addInputPort("inM3", Serializable.class);
	public final Port<Serializable> outY = 
			addOutputPort("outY", Serializable.class);
	public final Port<Serializable> inM4 = 
			addInputPort("inM4", Serializable.class);

	public ACTIVITY1 (){
		this("main",step);
	}
	public ACTIVITY1 (String name,double step){
		super(name);
		makeActivity();
		//addTestInput("in", new entity("job"));
	}

	private void makeActivity(){

		
		t1 t1_instance = new t1("?t1");
		addChildModel(t1_instance);


			

		
		t2 t2_instance = new t2("?t2");
		addChildModel(t2_instance);


			

		
		t3 t3_instance = new t3("?t3");
		addChildModel(t3_instance);


			

		
		j1 j1_instance = new j1("?j1");
		addChildModel(j1_instance);


			

		
		j2 j2_instance = new j2("?j2");
		addChildModel(j2_instance);


			
		List<String> syncInport = new ArrayList<String>();
		syncInport.add("inFLOW6");

		syncInport.add("inFLOW7");

		syncInport.add("inFLOW15");

		syncInport.add("inFLOW17");
		List<String> syncOutport = new ArrayList<String>();
		syncOutport.add("outFLOW8");

		syncOutport.add("outFLOW18");
		sync sync_instance = new sync("sync", step, 
											syncInport, syncOutport);
		addChildModel(sync_instance);


		
		j31 j31_instance = new j31("?j31");
		addChildModel(j31_instance);


			

		
		j32 j32_instance = new j32("?j32");
		addChildModel(j32_instance);


			
		List<String> BEHAVIOR1Inport = new ArrayList<String>();
		BEHAVIOR1Inport.add("inFLOW11");
		List<String> BEHAVIOR1Outport = new ArrayList<String>();
		BEHAVIOR1Outport.add("outFLOW12");

		BEHAVIOR1Outport.add("outFLOW13");
		BEHAVIOR1 BEHAVIOR1_instance = new BEHAVIOR1("", step, 
											BEHAVIOR1Inport, BEHAVIOR1Outport);
		addChildModel(BEHAVIOR1_instance);

		BEHAVIOR2 BEHAVIOR2_instance = new BEHAVIOR2("");
		addChildModel(BEHAVIOR2_instance);



		
		t4 t4_instance = new t4("?t4");
		addChildModel(t4_instance);


			
		BEHAVIOR3 BEHAVIOR3_instance = new BEHAVIOR3("");
		addChildModel(BEHAVIOR3_instance);



		
		j4 j4_instance = new j4("?j4");
		addChildModel(j4_instance);


			

		
		drop drop_instance = new drop("drop");
		addChildModel(drop_instance);


			
		BEHAVIOR4 BEHAVIOR4_instance = new BEHAVIOR4("");
		addChildModel(BEHAVIOR4_instance);



		
		threshold threshold_instance = new threshold("threshold?");
		addChildModel(threshold_instance);


			

		
		diffusion diffusion_instance = new diffusion("diffusion");
		addChildModel(diffusion_instance);


			
		addCoupling(this.inM1,t1_instance.inFLOW1);

		addCoupling(this.inM2,t2_instance.inFLOW2);

		addCoupling(this.inM3,t3_instance.inFLOW3);

		addCoupling(t1_instance.outFLOW4,j1_instance.inFLOW4);
		addCoupling(t2_instance.outFLOW5,j2_instance.inFLOW5);
		addCoupling(j2_instance.outFLOW6,sync_instance.inFLOW6);
		addCoupling(j1_instance.outFLOW7,sync_instance.inFLOW7);
		addCoupling(sync_instance.outFLOW8,this.outY);

		addCoupling(this.inM4,t4_instance.inFLOW9);

		addCoupling(t4_instance.outFLOW10,BEHAVIOR3_instance.inFLOW10);
		addCoupling(t3_instance.outFLOW11,BEHAVIOR1_instance.inFLOW11);
		addCoupling(BEHAVIOR1_instance.outFLOW12,j31_instance.inFLOW12);
		addCoupling(BEHAVIOR1_instance.outFLOW13,j32_instance.inFLOW13);
		addCoupling(j31_instance.outFLOW14,BEHAVIOR2_instance.inFLOW14);
		addCoupling(BEHAVIOR2_instance.outFLOW15,sync_instance.inFLOW15);
		addCoupling(BEHAVIOR3_instance.outF_95,j4_instance.inF_95);
		addCoupling(BEHAVIOR3_instance.outF_5,drop_instance.inF_5);
		addCoupling(j4_instance.outFLOW16,BEHAVIOR4_instance.inFLOW16);
		addCoupling(BEHAVIOR4_instance.outFLOW17,sync_instance.inFLOW17);
		addCoupling(sync_instance.outFLOW18,threshold_instance.inFLOW18);
		addCoupling(diffusion_instance.outFLOW19,BEHAVIOR4_instance.inFLOW19);
		addCoupling(threshold_instance.outFLOW20,diffusion_instance.inFLOW20);

	}
}
