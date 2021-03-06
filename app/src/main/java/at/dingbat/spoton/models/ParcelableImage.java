package at.dingbat.spoton.models;

import android.os.Parcel;
import android.os.Parcelable;

import kaaes.spotify.webapi.android.models.Image;

/**
 * Created by bendix on 06.06.15.
 */
public class ParcelableImage extends Image implements Parcelable {
    public static Creator CREATOR = new Creator() {

        @Override
        public ParcelableImage createFromParcel(Parcel source) {
            return new ParcelableImage(source);
        }

        @Override
        public ParcelableImage[] newArray(int size) {
            return new ParcelableImage[size];
        }
    };

    public ParcelableImage(Image image) {
        this.width = image.width;
        this.height = image.height;
        this.url = image.url;
    }

    public ParcelableImage(Parcel parcel) {
        this.width = parcel.readInt();
        this.height = parcel.readInt();
        this.url = parcel.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(width);
        parcel.writeInt(height);
        parcel.writeString(url);
    }

}
