package com.mumu.bluetooth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class PadView extends ImageView {

	Paint mPaint;
	int w, h, r, x, y;

	public PadView(Context context) {
		super(context);
		init();
	}

	public PadView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		mPaint = new Paint();
		post(new Runnable() {
			@Override
			public void run() {
				w = getMeasuredWidth();
				h = getMeasuredHeight();
				r = Math.min(w, h) / 2;
				x = w / 2;
				y = h / 2;
			}
		});
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if(w == 0 || h == 0){
			w = getMeasuredWidth();
			h = getMeasuredHeight();
			r = Math.min(w, h) / 2;
		//	x = w / 2;
		//	y = h / 2;
		}
		mPaint.setColor(Color.RED);
		canvas.drawCircle(w / 2, h / 2, r, mPaint);
		mPaint.setStrokeWidth(18);
		mPaint.setColor(Color.BLUE);
		canvas.drawPoint(x, y, mPaint);
		mPaint.reset();
	}
	
	public void draw(float x,float y){
		this.x = (int)((1f-x)*w);
		this.y = (int)((1f-y)*h);
		Log.d("bt_pad", "input = ("+x+","+y+"),get = ("+this.x+","+this.y+")");
		invalidate();
	}

}
