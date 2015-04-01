package com.googlecode.mgwt.image.client;

import com.google.gwt.resources.client.ImageResource;

public interface ImageConverterCallback {
	public void onSuccess(ImageResource imageResource);

	public void onFailure(Throwable e);
}