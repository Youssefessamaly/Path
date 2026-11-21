-- Expands the original 12-skill catalog (V4) with commonly needed technologies that CVs and
-- internship postings already reference but could never be detected, selected, or required
-- before now. Deliberately excludes bare single-letter/ambiguous tokens (e.g. "C", "R", "Go")
-- that would be prone to false-positive substring matches in ordinary text — see
-- CvSkillExtractionServiceTest for the matcher-level C/C++ safety checks instead.
INSERT INTO skills (id, name) VALUES
    (gen_random_uuid(), 'PostgreSQL'),
    (gen_random_uuid(), 'MySQL'),
    (gen_random_uuid(), 'MongoDB'),
    (gen_random_uuid(), 'Docker'),
    (gen_random_uuid(), 'Git'),
    (gen_random_uuid(), 'Node.js'),
    (gen_random_uuid(), 'HTML'),
    (gen_random_uuid(), 'CSS'),
    (gen_random_uuid(), 'C++'),
    (gen_random_uuid(), 'C#'),
    (gen_random_uuid(), 'PHP'),
    (gen_random_uuid(), 'Angular'),
    (gen_random_uuid(), 'Vue.js'),
    (gen_random_uuid(), 'AWS'),
    (gen_random_uuid(), 'Kotlin'),
    (gen_random_uuid(), 'Swift'),
    (gen_random_uuid(), 'Flutter'),
    (gen_random_uuid(), 'React Native'),
    (gen_random_uuid(), 'REST API'),
    (gen_random_uuid(), 'Linux');

-- Aliases for skills that already existed before this migration.
INSERT INTO skill_aliases (skill_id, alias)
SELECT id, alias FROM skills, (VALUES
    ('JavaScript', 'JS'),
    ('TypeScript', 'TS'),
    ('React', 'React.js'),
    ('React', 'ReactJS'),
    ('Spring Boot', 'SpringBoot'),
    ('Spring Boot', 'Spring-Boot'),
    ('Power BI', 'PowerBI'),
    ('Excel', 'Microsoft Excel')
) AS seed(skill_name, alias)
WHERE skills.name = seed.skill_name;

-- Aliases for skills added above in this same migration.
INSERT INTO skill_aliases (skill_id, alias)
SELECT id, alias FROM skills, (VALUES
    ('PostgreSQL', 'Postgres'),
    ('Node.js', 'NodeJS'),
    ('Node.js', 'Node JS'),
    ('MongoDB', 'Mongo'),
    ('Vue.js', 'VueJS'),
    ('Vue.js', 'Vue'),
    ('React Native', 'ReactNative'),
    ('C++', 'Cpp'),
    ('C#', 'C Sharp'),
    ('AWS', 'Amazon Web Services'),
    ('HTML', 'HTML5'),
    ('CSS', 'CSS3'),
    ('REST API', 'RESTful API')
) AS seed(skill_name, alias)
WHERE skills.name = seed.skill_name;
