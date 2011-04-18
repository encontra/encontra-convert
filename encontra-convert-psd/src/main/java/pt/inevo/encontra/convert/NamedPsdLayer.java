package pt.inevo.encontra.convert;

import psd.layer.PsdLayer;

public class NamedPsdLayer {

	private final PsdLayer layer;
	
	private String name;
	
	public NamedPsdLayer(PsdLayer layer) {
		this.layer = layer;
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the layer
	 */
	public PsdLayer getLayer() {
		return layer;
	}

	@Override
	public String toString() {
		if (this.name != null) {
			return this.name;
		}
		else if (this.layer != null) {
			return this.layer.getName();
		}
		else {
			return super.toString();
		}
	}
	
}
