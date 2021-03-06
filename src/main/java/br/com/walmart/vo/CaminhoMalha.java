package br.com.walmart.vo;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Representa a aresta ou vias (ruas, avenidas, estrada) dos grafos de malha viária.
 * 
 * É composto por um ponto de partida, ponto de fim e distância entre estes dois.
 * 
 * @author darcio
 * 
 */
@JsonSerialize
public class CaminhoMalha implements Serializable {

	private static final long serialVersionUID = 7231543014974883852L;

	private String startLocation;
	private String endLocation;
	private Double distance;

	public CaminhoMalha() {
	}
	
	public CaminhoMalha(String startLocation, String endLocation, Double distance) {
		super();
		this.startLocation = startLocation;
		this.endLocation = endLocation;
		this.distance = distance;
	}

	public String getStartLocation() {
		return startLocation;
	}
	

	public void setStartLocation(String startLocation) {
		this.startLocation = startLocation;
	}

	public String getEndLocation() {
		return endLocation;
	}

	public void setEndLocation(String endLocation) {
		this.endLocation = endLocation;
	}

	public Double getDistance() {
		return distance;
	}

	public void setDistance(Double distance) {
		this.distance = distance;
	}


	/**
	 * Testes basicos + Teste se todos os atributos do caminho são iguais
	 */
	@Override
	public boolean equals(Object o) {
		
		if (this == o)
			return true;

		if (o == null || getClass() != o.getClass())
			return false;

		CaminhoMalha caminho = (CaminhoMalha) o;

		
		return EqualsBuilder.reflectionEquals(this, caminho);

	}
	

	/**
	 * Considera no hash todos os atributos
	 */
	@Override
	public int hashCode() {
		
		return new HashCodeBuilder(5673,46465).append(this.startLocation).append(this.endLocation).append(this.distance).hashCode();
		
	}
	
	
}
