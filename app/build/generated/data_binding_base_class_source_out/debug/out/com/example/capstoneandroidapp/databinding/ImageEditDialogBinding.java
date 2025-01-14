// Generated by view binder compiler. Do not edit!
package com.example.capstoneandroidapp.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import com.example.capstoneandroidapp.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ImageEditDialogBinding implements ViewBinding {
  @NonNull
  private final LinearLayout rootView;

  @NonNull
  public final ImageView dlgImage;

  @NonNull
  public final EditText dlgInput;

  @NonNull
  public final TextView dlgTitle;

  private ImageEditDialogBinding(@NonNull LinearLayout rootView, @NonNull ImageView dlgImage,
      @NonNull EditText dlgInput, @NonNull TextView dlgTitle) {
    this.rootView = rootView;
    this.dlgImage = dlgImage;
    this.dlgInput = dlgInput;
    this.dlgTitle = dlgTitle;
  }

  @Override
  @NonNull
  public LinearLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ImageEditDialogBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ImageEditDialogBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.image_edit_dialog, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ImageEditDialogBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.dlg_image;
      ImageView dlgImage = rootView.findViewById(id);
      if (dlgImage == null) {
        break missingId;
      }

      id = R.id.dlg_input;
      EditText dlgInput = rootView.findViewById(id);
      if (dlgInput == null) {
        break missingId;
      }

      id = R.id.dlg_title;
      TextView dlgTitle = rootView.findViewById(id);
      if (dlgTitle == null) {
        break missingId;
      }

      return new ImageEditDialogBinding((LinearLayout) rootView, dlgImage, dlgInput, dlgTitle);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
