package com.cjlyth.sousvide.pi.entity;

public class LogTemp {
	private Double temperature;

	public LogTemp() {
	}

	public LogTemp(Double temperature) {
		super();
		this.temperature = temperature;
	}

	/**
	 * @return the temperature
	 */
	public Double getTemperature() {
		return temperature;
	}

	/**
	 * @param temperature the temperature to set
	 */
	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "LogTemp [temperature=" + temperature + "]";
	}

}
