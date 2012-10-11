package org.encog.app.generate;

import java.io.File;
import java.util.Date;

import org.encog.Encog;
import org.encog.app.analyst.EncogAnalyst;
import org.encog.app.analyst.script.prop.ScriptProperties;
import org.encog.app.generate.generators.LanguageSpecificGenerator;
import org.encog.app.generate.generators.ProgramGenerator;
import org.encog.app.generate.generators.TemplateGenerator;
import org.encog.app.generate.generators.cs.GenerateCS;
import org.encog.app.generate.generators.java.GenerateEncogJava;
import org.encog.app.generate.generators.js.GenerateEncogJavaScript;
import org.encog.app.generate.generators.mql4.GenerateMQL4;
import org.encog.app.generate.generators.ninja.GenerateNinjaScript;
import org.encog.app.generate.program.EncogProgram;
import org.encog.app.generate.program.EncogProgramNode;
import org.encog.ml.MLEncodable;
import org.encog.ml.MLMethod;
import org.encog.neural.networks.BasicNetwork;
import org.encog.persist.EncogDirectoryPersistence;

public class EncogCodeGeneration {

	private final TargetLanguage targetLanguage;
	private boolean embedData;
	private LanguageSpecificGenerator generator;
	private final EncogProgram program = new EncogProgram();

	public EncogCodeGeneration(TargetLanguage theTargetLanguage) {
		this.targetLanguage = theTargetLanguage;

		switch (theTargetLanguage) {
		case NoGeneration:
			throw new AnalystCodeGenerationError("No target language has been specified for code generation.");
		case Java:
			this.generator = new GenerateEncogJava();
			break;
		case CSharp:
			this.generator = new GenerateCS();
			break;
		case MQL4:
			this.generator = new GenerateMQL4();
			break;
		case NinjaScript:
			this.generator = new GenerateNinjaScript();
			break;
		case JavaScript:
			this.generator = new GenerateEncogJavaScript();
			break;
		
		}
	}

	/**
	 * @return the targetLanguage
	 */
	public TargetLanguage getTargetLanguage() {
		return targetLanguage;
	}

	public void save(File file) {
		this.generator.writeContents(file);
	}

	public String save() {
		return this.generator.getContents();
	}

	private EncogProgramNode generateForMethod(EncogProgramNode mainClass,
			File method) {

		if (this.embedData) {
			MLEncodable encodable = (MLEncodable) EncogDirectoryPersistence
					.loadObject(method);
			double[] weights = new double[encodable.encodedArrayLength()];
			encodable.encodeToArray(weights);
			mainClass.createArray("WEIGHTS", weights);
		}

		return mainClass.createNetworkFunction("createNetwork", method);
	}

	public void generate(File method, File data) {
		EncogProgramNode createNetworkFunction = null;
		this.program.addComment("Code generated by Encog v" + Encog.getInstance().getProperties().get(Encog.ENCOG_VERSION));
		this.program.addComment("Generation Date: " + (new Date()).toString());
		this.program.addComment("Generated code may be used freely");
		this.program.addComment("http://www.heatonresearch.com/encog");
		EncogProgramNode mainClass = this.program.createClass("EncogExample");

		if (data != null) {
			mainClass.embedTraining(data);
			if( !(this.generator instanceof GenerateEncogJavaScript) ) {
				mainClass.generateLoadTraining(data);	
			}
			mainClass.generateLoadTraining(data);
		}

		if (method != null) {
			createNetworkFunction = generateForMethod(mainClass, method);
		}

		EncogProgramNode mainFunction = mainClass.createMainFunction();

		if (createNetworkFunction != null) {
			mainFunction.createFunctionCall(createNetworkFunction, "MLMethod",
					"method");
		}

		if (data != null) {
			if( !(this.generator instanceof GenerateEncogJavaScript) ) {
				mainFunction.createFunctionCall("createTraining", "MLDataSet",
					"training");
			}
		}
		mainFunction
				.addComment("Network and/or data is now loaded, you can add code to train, evaluate, etc.");

		((ProgramGenerator) this.generator).generate(this.program,
				this.embedData);
	}

	public void generate(EncogAnalyst analyst) {

		if (this.generator instanceof ProgramGenerator) {
			final String methodID = analyst
					.getScript()
					.getProperties()
					.getPropertyString(
							ScriptProperties.ML_CONFIG_MACHINE_LEARNING_FILE);

			final String trainingID = analyst
					.getScript()
					.getProperties()
					.getPropertyString(ScriptProperties.ML_CONFIG_TRAINING_FILE);

			final File methodFile = analyst.getScript().resolveFilename(
					methodID);
			final File trainingFile = analyst.getScript().resolveFilename(
					trainingID);

			generate(methodFile, trainingFile);
		} else {
			((TemplateGenerator) this.generator).generate(analyst);
		}
	}

	public boolean isEmbedData() {
		return embedData;
	}

	public void setEmbedData(boolean embedData) {
		this.embedData = embedData;
	}

	public static boolean isSupported(MLMethod method) {
		if (method instanceof BasicNetwork) {
			return true;
		} else {
			return false;
		}
	}

}
