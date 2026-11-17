package com.internplatform.profilephoto;

import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.internprofile.InternProfile;

/** Relative image URLs: no storage key or filesystem path is exposed to clients. */
public final class ProfilePhotoUrls {
    private ProfilePhotoUrls() { }

    public static String intern(InternProfile profile) {
        return profile == null || profile.getPhotoStorageKey() == null ? null
                : "/api/v1/profile-images/interns/" + profile.getId() + "?v=" + version(profile.getPhotoStorageKey());
    }

    public static String company(CompanyProfile profile) {
        return profile == null || profile.getPhotoStorageKey() == null ? null
                : "/api/v1/profile-images/companies/" + profile.getId() + "?v=" + version(profile.getPhotoStorageKey());
    }

    private static String version(String key) {
        String filename = key.substring(key.lastIndexOf('/') + 1);
        return filename.substring(0, filename.lastIndexOf('.'));
    }
}
