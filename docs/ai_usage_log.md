# PlanCast AI 활용 개발 기록

## 1. 프로젝트 구조 분석

### 사용한 AI 도구
- Codex

### AI에게 요청한 내용
- Android Studio에서 생성된 Java/XML 기반 Android 프로젝트의 현재 구조를 분석하도록 요청했다.
- PlanCast 앱의 목표에 맞는 패키지 구조, 기능 구현 순서, Gradle 설정 주의사항을 제안하도록 요청했다.
- 이 단계에서는 코드를 수정하지 않고 분석만 수행하도록 요청했다.

### AI가 생성하거나 수정한 코드
- 없음.
- 프로젝트 파일을 읽고 현재 구조를 분석했다.

### 팀원이 검토한 내용
- 현재 프로젝트가 기본 Android Java/XML 템플릿에 가까운 상태임을 확인했다.
- `MainActivity.java`, `activity_main.xml`, `AndroidManifest.xml`, Gradle 설정 파일 구성을 확인했다.
- Room, Retrofit, 위치, 알림, RecyclerView 기능이 아직 구현되지 않았음을 확인했다.

### 직접 수정하거나 확인한 내용
- 앱 이름이 `PlanCast`로 설정되어 있는지 확인했다.
- 기본 패키지가 `com.sch.plancast`임을 확인했다.
- Java/XML 기반을 유지해야 하며 Kotlin과 Jetpack Compose를 사용하지 않는다는 개발 방향을 정리했다.

### 빌드 및 실행 테스트 결과
- 이 단계에서는 코드 변경이 없어서 별도 빌드는 수행하지 않았다.
- 이후 기능 개발을 위한 구조 설계 기준을 확정했다.

## 2. Gradle 의존성 설정

### 사용한 AI 도구
- Codex

### AI에게 요청한 내용
- PlanCast 개발에 필요한 Gradle 의존성만 추가하도록 요청했다.
- Room Database, Retrofit2, Gson Converter, Google Play Services Location, RecyclerView, Material Components를 설정하도록 요청했다.
- Room은 `kapt`가 아니라 Java용 `annotationProcessor`를 사용하도록 요청했다.
- OpenWeatherMap API Key를 코드에 하드코딩하지 않고 `local.properties`에서 읽어 `BuildConfig`로 전달할 수 있게 준비하도록 요청했다.

### AI가 생성하거나 수정한 코드
- `gradle/libs.versions.toml`
  - Room, Retrofit2, Gson Converter, Play Services Location, RecyclerView 의존성 alias를 추가했다.
  - Java 조건에 맞게 `activity-ktx` 대신 `androidx.activity:activity` alias를 사용하도록 정리했다.
- `app/build.gradle.kts`
  - 필요한 라이브러리 의존성을 추가했다.
  - `annotationProcessor(libs.room.compiler)`를 추가했다.
  - `buildFeatures { buildConfig = true }`를 추가했다.
  - `local.properties`의 `OPEN_WEATHER_MAP_API_KEY` 값을 `BuildConfig.OPEN_WEATHER_MAP_API_KEY`로 전달하도록 설정했다.

### 팀원이 검토한 내용
- Kotlin 코드와 Compose 설정이 추가되지 않았는지 확인했다.
- Room compiler가 `kapt`가 아니라 `annotationProcessor`로 설정되었는지 확인했다.
- API Key가 소스 코드에 직접 하드코딩되지 않았는지 확인했다.

### 직접 수정하거나 확인한 내용
- `local.properties`에 API Key를 추가하면 Gradle이 `BuildConfig` 필드로 전달할 수 있는 구조임을 확인했다.
- Gradle 설정만 수정했고 Activity, XML, Manifest는 이 단계에서 수정하지 않았음을 확인했다.

### 빌드 및 실행 테스트 결과
- `./gradlew.bat assembleDebug` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 새 의존성 추가 후에도 Java/XML 기반 프로젝트가 정상 빌드됨을 확인했다.

## 3. Room DB 계층 구현

### 사용한 AI 도구
- Codex

### AI에게 요청한 내용
- 일정 데이터를 로컬에 저장하기 위한 Room Database 계층만 구현하도록 요청했다.
- `data/local`, `data/repository` 패키지를 만들도록 요청했다.
- `ScheduleEntity.java`, `ScheduleDao.java`, `AppDatabase.java`, `ScheduleRepository.java`를 생성하도록 요청했다.
- DB 작업이 메인 스레드에서 직접 실행되지 않도록 Repository에서 백그라운드 처리를 사용하도록 요청했다.

### AI가 생성하거나 수정한 코드
- `app/src/main/java/com/sch/plancast/data/local/ScheduleEntity.java`
  - `schedules` 테이블 엔티티를 생성했다.
  - `id`, `title`, `date`, `time`, `activityType`, `memo`, `createdAt` 필드를 정의했다.
  - `INDOOR`, `OUTDOOR` 활동 유형 상수를 추가했다.
- `app/src/main/java/com/sch/plancast/data/local/ScheduleDao.java`
  - `insert`, `update`, `delete`, `getAll`, `getByDate`, `getById` 메서드를 정의했다.
  - 날짜와 시간 기준 정렬 쿼리를 추가했다.
- `app/src/main/java/com/sch/plancast/data/local/AppDatabase.java`
  - Room Database 싱글턴을 구성했다.
  - DB 이름을 `plancast.db`로 설정했다.
- `app/src/main/java/com/sch/plancast/data/repository/ScheduleRepository.java`
  - DAO 접근을 Repository 계층으로 감쌌다.
  - `ExecutorService`를 사용해 DB 작업을 백그라운드에서 실행하도록 구현했다.
  - 작업 결과를 받을 수 있는 callback 인터페이스를 추가했다.

### 팀원이 검토한 내용
- Entity 필드가 요구사항과 일치하는지 확인했다.
- DAO 메서드가 일정 CRUD와 날짜별 조회에 필요한 기능을 제공하는지 확인했다.
- Activity나 XML UI가 이 단계에서 수정되지 않았는지 확인했다.

### 직접 수정하거나 확인한 내용
- Room DB 접근이 Repository를 통해 이루어지도록 구조를 확인했다.
- 메인 스레드에서 직접 DB 작업을 수행하지 않도록 `ExecutorService` 사용을 확인했다.
- Java 코드만 추가되었고 Kotlin/Compose 코드는 추가되지 않았음을 확인했다.

### 빌드 및 실행 테스트 결과
- `./gradlew.bat assembleDebug` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- Room annotation processor가 정상 동작하여 생성 코드까지 포함한 빌드가 성공했다.

## 4. 일정 CRUD UI 구현

### 사용한 AI 도구
- Codex

### AI에게 요청한 내용
- 사용자가 일정을 등록, 조회, 수정, 삭제할 수 있는 CRUD UI를 구현하도록 요청했다.
- `CalendarView`로 날짜를 선택하고, 선택 날짜의 일정을 `RecyclerView`에 표시하도록 요청했다.
- 일정 추가/수정 화면에서 제목, 날짜, 시간, 활동 유형, 메모를 입력하도록 요청했다.
- 날짜는 `DatePickerDialog`, 시간은 `TimePickerDialog`로 선택할 수 있도록 요청했다.
- 날씨, 위치, 알림 기능은 아직 구현하지 않도록 요청했다.

### AI가 생성하거나 수정한 코드
- `app/src/main/java/com/sch/plancast/MainActivity.java`
  - `CalendarView`, `RecyclerView`, 빈 일정 메시지, 일정 추가 FAB를 연결했다.
  - 선택 날짜 기준으로 Repository를 통해 일정을 조회하고 UI를 갱신했다.
- `app/src/main/res/layout/activity_main.xml`
  - 메인 화면에 CalendarView, RecyclerView, 빈 상태 메시지, FloatingActionButton을 배치했다.
- `app/src/main/java/com/sch/plancast/ui/schedule/ScheduleAdapter.java`
  - 일정 제목, 시간, 활동 유형, 메모 미리보기를 목록에 표시하도록 구현했다.
  - 항목 클릭 시 일정 id를 전달해 수정 화면을 열도록 구현했다.
- `app/src/main/res/layout/item_schedule.xml`
  - RecyclerView 항목 레이아웃을 생성했다.
- `app/src/main/java/com/sch/plancast/ui/schedule/ScheduleFormActivity.java`
  - 새 일정 등록 모드와 기존 일정 수정 모드를 모두 구현했다.
  - 저장 시 insert 또는 update를 수행하고, 수정 모드에서는 삭제 기능을 제공했다.
- `app/src/main/res/layout/activity_schedule_form.xml`
  - 일정 입력 폼 화면을 구성했다.
- `app/src/main/AndroidManifest.xml`
  - `ScheduleFormActivity`를 등록했다.

### 팀원이 검토한 내용
- 일정 등록, 조회, 수정, 삭제 흐름이 요구사항대로 동작하는지 검토했다.
- 선택한 날짜의 일정만 목록에 표시되는지 확인했다.
- 제목이 비어 있을 때 저장되지 않고 오류 메시지가 표시되는지 확인했다.
- 기존 Room DB 계층을 통해서만 DB 접근이 이루어지는지 확인했다.

### 직접 수정하거나 확인한 내용
- Android Studio에서 앱 실행 테스트를 진행했다.
- CalendarView 날짜 선택, FAB 일정 추가, 목록 클릭 수정, 삭제 후 목록 갱신 흐름을 확인했다.
- 날씨 API, 위치 권한, 알림 기능이 이 단계에서 추가되지 않았음을 확인했다.

### 빌드 및 실행 테스트 결과
- `./gradlew.bat assembleDebug` 실행 결과 `BUILD SUCCESSFUL`을 확인했다.
- 앱 실행 테스트 결과 일정 CRUD 기능이 정상 동작함을 확인했다.
