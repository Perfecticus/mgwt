package com.googlecode.mgwt.image.client;

import com.google.gwt.dom.client.ImageElement;

public interface LoadImageCallback {
	public void onSuccess(ImageElement imageElement);

	public void onFailure(Throwable e);
}