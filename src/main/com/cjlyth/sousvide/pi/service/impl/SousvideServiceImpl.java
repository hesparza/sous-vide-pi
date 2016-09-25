package com.cjlyth.sousvide.pi.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.cjlyth.sousvide.pi.entity.Configuration;
import com.cjlyth.sousvide.pi.entity.LogTemp;
import com.cjlyth.sousvide.pi.service.SousvideService;
import com.cjlyth.sousvide.pi.util.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

@Service
public class SousvideServiceImpl implements SousvideService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	HttpClient httpClient;
	
	@Value("${service.url}")
	private String serviceUrl;
	
	@Value("${endpoint.configuration}")
	private String endpointConfiguration;

	@Value("${endpoint.logs}")
	private String endpointLogs;
	
	@Value("${python.success.code}")
	private int pythonSuccessCode;
	
	@Value("${python.timeout.ms}")
	private int pythonTimeoutMs;

	@Value("${python.exe.abs.path}")
	private String pythonExeAbsPath;
	
	@Value("${read.serial.script.abs.path}")
	private String readSerialScriptAbsPath;
	
	@Value("${sleep.main.thread.milliseconds}")
	private int sleepMainThreadMilliseconds;
	
	private static final String GET = "GET";
	private static final String POST = "POST";
	private static final Double DEFAULT_TEMP = 0.0; //In Celcius
	private static final Double TEMP_TOLERANCE = 5.0;
	
	@Override
	public void startExecution() {
		final String METHOD_NAME = getClass().getName() + ".startExecution()";
		logger.info("{} started", METHOD_NAME);
		Configuration configuration;
		Double targetTemp = DEFAULT_TEMP;
		Double currentTemp = DEFAULT_TEMP;
		
		//Initialize
		GpioPinDigitalOutput output1 = initializeGPIO();
		CommandLine cmdLine = initializePythonCmdLine();
		if (cmdLine == null) {
			logger.error("{} ERROR!! Could not initialize command line, exiting..");
			return;
		}
		
		//Main loop
		for (;;) {
			
			//Get configuration
			configuration = getConfiguration();
			if (configuration == null) {
				logger.error("{} ERROR!! Could not get configuration correctly, using: {}", METHOD_NAME, configuration);
				targetTemp = DEFAULT_TEMP;
			}
			targetTemp =  configuration.getTemperature();
			
			//Read current temperature
			currentTemp = getCurrentTemperature(cmdLine);
			if (currentTemp.equals(DEFAULT_TEMP)) {
				logger.warn("{} WARNING!! Current temperature is set to {}", METHOD_NAME, DEFAULT_TEMP);
			}
	
			//Write current temperature
			final boolean tempPostedOk = postCurrentTemperature(new LogTemp(currentTemp));
			if (!tempPostedOk) {
				logger.error("{} ERROR!! Could not post temperature correctly", METHOD_NAME);
			}
	
			//Control heater
		    handleCurrentTemperature(targetTemp, currentTemp, output1);
		    
		    doSleep(sleepMainThreadMilliseconds);
		}		    
	    
	}
	
	private void doSleep(final int millis) {
		final String METHOD_NAME = getClass().getName() + ".doSleep()";
		logger.info("{} Sleeping for {} milliseconds", METHOD_NAME, millis);
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			logger.error("{} FATAL ERROR!! while trying to sleep the process for {} milliseconds. Exception message is: ", METHOD_NAME, millis, e.getMessage());
		}
	}
	private CommandLine initializePythonCmdLine() {
		final String METHOD_NAME = getClass().getName() + ".initializePythonCmdLine()";
		logger.info("{} Initilizing command line for python script", METHOD_NAME);
		
		CommandLine result;
		Path scriptPath = Paths.get(readSerialScriptAbsPath);
		boolean isScriptOk = Files.isRegularFile(scriptPath) && Files.isReadable(scriptPath) && Files.isExecutable(scriptPath);
		if(!isScriptOk) {
			logger.error("{} FATAL ERROR!! The provided python script for serial reading is not ok, please check permissions, file configured is: {}", METHOD_NAME, readSerialScriptAbsPath);
			result = null;
		} else {
			final String pythonScript = "pythonTemplate";
			Map<String, File> configMap = new HashMap<>();
			configMap.put(pythonScript, new File(readSerialScriptAbsPath));
			CommandLine cmdl = new CommandLine(pythonExeAbsPath);
			cmdl.addArgument("${" + pythonScript + "}");
			cmdl.setSubstitutionMap(configMap);
			result = cmdl;
		}
		return result;
	}
	
	private GpioPinDigitalOutput initializeGPIO() {
		final String METHOD_NAME = getClass().getName() + ".GpioPinDigitalOutput()";
		logger.info("{} Initilizing general outputs", METHOD_NAME);
	    GpioController gpio = GpioFactory.getInstance();	    
	    return gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01);
	}
	
	private Configuration getConfiguration() {
		final String METHOD_NAME = getClass().getName() + ".getConfiguration()";
		Configuration configuration;
		try {
			String result = httpClient.doHttpRequest(serviceUrl, endpointConfiguration, null, GET);
			if (result == null || StringUtils.isEmpty(result)) {
				logger.error("{} ERROR!!! Could not retrieve the configuration from endpoint: {}", METHOD_NAME, serviceUrl + "/" + endpointConfiguration);	
			}
			logger.info("{} The result was: {}", METHOD_NAME, result);

			//JSON from URL to Object
			configuration = new ObjectMapper().readValue(result, Configuration.class);
			
			logger.info("{} Configuration obtained successfully: {}", METHOD_NAME, configuration.toString());
			
		} catch (IOException e) {
			logger.error(METHOD_NAME + " throw exception: " + e.getMessage());
			e.printStackTrace();
			configuration = null;
		}
		return configuration;
	}
	
	private boolean postCurrentTemperature(final LogTemp logTemp) {
		final String METHOD_NAME = getClass().getName() + ".postCurrentTemperature()";
		boolean tempPostedOk;
		try {
			logger.info("{} Posting temperature: {}", METHOD_NAME, logTemp);
			String result = httpClient.doHttpRequest(serviceUrl, endpointLogs, new ObjectMapper().writeValueAsString(logTemp), POST);
			
			logger.info("{} Temperature posted successfully: {}, response received: {}", METHOD_NAME, logTemp.toString(), result);
			tempPostedOk = true;
			
		} catch (IOException e) {
			logger.error(METHOD_NAME + " throw exception: " + e.getMessage());
			e.printStackTrace();
			tempPostedOk = false;
		}
		return tempPostedOk;
	}
	
	private Double getCurrentTemperature(CommandLine cmdLine) {
		final String METHOD_NAME = getClass().getName() + ".getCurrentTemperature()";
		
		Double newTemp;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(pythonSuccessCode);
		executor.setWatchdog(new ExecuteWatchdog(pythonTimeoutMs));
		executor.setStreamHandler(new PumpStreamHandler(baos));

		try {
			int exitValue = executor.execute(cmdLine);
			if (exitValue != pythonSuccessCode) {
				logger.error("{} ERROR!! while executing python script to retrieve current temperature, setting it to default value: {}", METHOD_NAME, DEFAULT_TEMP);
				newTemp = DEFAULT_TEMP;
			}
			newTemp = Double.valueOf(baos.toString());
		} catch (IOException ioe) {
			logger.error("{} ERROR!! while executing python script to retrieve current temperature, setting it to default value: {}, exception message: {}", METHOD_NAME, DEFAULT_TEMP, ioe.getMessage());
			newTemp = DEFAULT_TEMP;
		} catch (NumberFormatException nfe) {
			logger.error("{} ERROR!! while formatting output coming from pythin script, setting current temperature to default value: {}, output received: {}, exception message: {}", METHOD_NAME, DEFAULT_TEMP, baos.toString(), nfe.getMessage());
			newTemp = DEFAULT_TEMP;
		}
		return newTemp;
	}
	
	private void handleCurrentTemperature(final Double targetTemp, final Double currentTemp, final GpioPinDigitalOutput output1) {
		final String METHOD_NAME = getClass().getName() + ".handleCurrentTemperature()";
		final Double upperLimit = targetTemp + TEMP_TOLERANCE;
		final Double lowerLimit = targetTemp - TEMP_TOLERANCE;
		
		if (currentTemp == DEFAULT_TEMP) {
			logger.warn("{} Current temperature is the default temperature, turning heater off for security reasons. Default temperature configured: {}", METHOD_NAME, DEFAULT_TEMP);
			output1.setState(PinState.LOW);
		} else if (currentTemp > upperLimit) {
			logger.info("{} Turning heater off", METHOD_NAME);
			output1.setState(PinState.LOW);
		} else if (currentTemp < lowerLimit) {
			logger.info("{} Turning heater on", METHOD_NAME);
			output1.setState(PinState.HIGH);
		} else {
			logger.info("{} Nothing to do, heater is currently: {}, current temperature is: {}", METHOD_NAME, output1.getState(), currentTemp);
		}
	}
	
}
