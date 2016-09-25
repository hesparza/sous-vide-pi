package com.cjlyth.sousvide.pi.service.impl;

import java.io.IOException;

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
	
	private static final String GET = "GET";
	private static final String POST = "POST";
	
	@Override
	public void startExecution() {
		final String METHOD_NAME = getClass().getName() + ".startExecution()";
		logger.info("{} started", METHOD_NAME);

		ObjectMapper mapper = new ObjectMapper();
		//Get configuration
		try {
			String result = httpClient.doHttpRequest(serviceUrl, endpointConfiguration, null, GET);
			if (result == null || StringUtils.isEmpty(result)) {
				logger.error("{} ERROR!!! Could not retrieve the configuration from endpoint: {}", METHOD_NAME, serviceUrl + "/" + endpointConfiguration);	
			}
			logger.info("{} The result was: {}", METHOD_NAME, result);

			//JSON from URL to Object
			Configuration configuration = mapper.readValue(result, Configuration.class);
			
			logger.info("{} Configuration obtained successfully: {}", METHOD_NAME, configuration.toString());
			
		} catch (IOException e) {
			logger.error(METHOD_NAME + " throw exception: " + e.getMessage());
			e.printStackTrace();
		}
		
/*		//Read temperature
		MCP3008Gpio temperatureReader = new MCP3008Gpio(); 
		
		try {
			temperatureReader.execute();
		} catch (IOException | InterruptedException e) {
			logger.error("{} throw exception: " + e.getMessage());
			e.printStackTrace();
		}*/
		
		//Write temperature
		
		//Log configuration
		try {
			LogTemp logTemp = new LogTemp();
			logTemp.setTemperature(5.25);
			logger.info("{} Posting temperature: {}", METHOD_NAME, logTemp);
			String result = httpClient.doHttpRequest(serviceUrl, endpointLogs, mapper.writeValueAsString(logTemp), POST);
			if (result == null || StringUtils.isEmpty(result)) {
				logger.error("{} ERROR!!! Could not post the configuration to endpoint: {}", METHOD_NAME, serviceUrl + "/" + endpointLogs);	
			}
			logger.info("{} The result was: {}", METHOD_NAME, result);

			logger.info("{} Configuration obtained successfully: {}", METHOD_NAME, logTemp.toString());
			
		} catch (IOException e) {
			logger.error(METHOD_NAME + " throw exception: " + e.getMessage());
			e.printStackTrace();
		}
		
		//Control heater
	    GpioController gpio = GpioFactory.getInstance();	    
	    final GpioPinDigitalOutput output1;
	    output1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01);
	    logger.info("About to send pulse");
	    output1.pulse(3000); 
	    

//        // provision gpio pin #01 as an output pin and turn on
//        final GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "MyLED", PinState.HIGH);
//
//        // set shutdown state for this pin
//        pin.setShutdownOptions(true, PinState.LOW);
//
//        System.out.println("--> GPIO state should be: ON");
//
//        try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//        // turn off gpio pin #01
//        pin.low();
//        System.out.println("--> GPIO state should be: OFF");
	    
	}
	
}
