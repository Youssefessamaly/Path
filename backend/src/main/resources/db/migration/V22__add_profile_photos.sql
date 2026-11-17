-- Photo uploads are optional; absent values preserve initials-based avatars.
ALTER TABLE intern_profiles ADD COLUMN photo_storage_key VARCHAR(255);
ALTER TABLE company_profiles ADD COLUMN photo_storage_key VARCHAR(255);
