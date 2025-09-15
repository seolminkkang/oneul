[![hits](https://myhits.vercel.app/api/hit/https%3A%2F%2Fgithub.com%2Ftoday-is-first%2Foneul?color=purple&label=hits&size=small)](https://myhits.vercel.app)

## 🗂️ 팀 문서 & 자료 링크

<p align="center">
  <a href="https://oneul-pjt.notion.site/SSAFY-1d2bc43cad598007adaff9d1b7e0235f">📒 팀 노션</a> &nbsp;|&nbsp;
  <a href="https://oneul-pjt.notion.site/1d2bc43cad598052b36ce17db47ef476">📚 개발 위키</a> &nbsp;|&nbsp;
  <a href="https://oneul-pjt.notion.site/Ground-Rule-1d2bc43cad59801abca2d92893ca7093">📏 그라운드 룰</a> &nbsp;|&nbsp;
<!--   <a href="http://oneul.store/">🚀 데모 페이지</a> -->
</p>

<br/>
<br/>

## 🧑‍💻 역할 및 기여

| 담당자 | 주요 작업 |
|--------|------------------------------------------------------------------|
| **이찬** | - **실시간 채팅 로딩 지연 문제 해결**<br>- **데이터 캐싱 기반 요청 최적화**<br>- Presigned-URL 기반 이미지 업로드 기능<br>- 홈 스트릭/피드/통계 기능 구현 및 캘린더 연동<br>- WebSocket 실시간 채팅 기능<br>- 챌린지, 피드, 결제 내역 조회하는 마이페이지 구현 |


<br/>
<br/>

## 👋 프로젝트 소개

### 🏆 *"오늘의 운동을 인증하고, 리워드를 받는 챌린지 플랫폼"*

**oneul**은 사용자가 **운동 인증을 통해 챌린지에 참여**하고,  
**목표 달성 시 환급 리워드**를 받을 수 있는 **챌린지 기반 웹 서비스**입니다.

- 사용자는 **인증 피드를 업로드**하며 도전 현황을 기록할 수 있어요.  
- 참여자 간에는 **실시간 채팅으로 소통**하며 도전을 이어갑니다.

<br/>
<br/>

## 🧩 **고민과 해결 방안**

### 🚀 사용자 경험과 성능 향상을 위한 서비스 고도화
서비스 확장과 사용자 경험 개선을 위해 세 가지 기술을 적용해 고도화를 진행했습니다.

1. 인피니티 스크롤을 도입해 최초 30개 데이터만 로딩하고, 이후 스크롤 시 과거 데이터를 순차적으로 불러오도록 개선했습니다. 이 방식으로 초기 로딩 시간을 약 15% 가량 줄였고, 렌더링 성능도 안정화되었습니다.

2. React Query 캐싱을 통해 동일 요청 반복을 방지했습니다. Stale Time과 캐시 전략 설정으로 API 호출 수를 약 35% 감소시켜 서버 부하를 효과적으로 줄였습니다.

3. 입력창과 스크롤 이벤트처럼 반복 호출이 잦은 구간에 Debounce와 Throttle을 적용했습니다.
그 결과 의도치 않은 중복 요청을 막을 수 있었고, 사용자 인터랙션의 일관성과 서버 안정성을 동시에 확보할 수 있었습니다.

[Wiki로 자세히 보기](https://github.com/today-is-first/oneul/wiki/%EC%B0%AC%EC%9D%98-%EA%B3%A0%EB%AF%BC%EA%B3%BC-%ED%95%B4%EA%B2%B0#%EC%B0%AC%EC%9D%98-%EA%B3%A0%EB%AF%BC%EA%B3%BC-%ED%95%B4%EA%B2%B0)

### 🚀 **임시 주문서 저장 전략 설계**

임시 주문서 생성은 결제 검증을 위한 필수 과정이지만, 이 데이터가 적절히 삭제되지 않으면 고아 데이터로 누적되어 스토리지·인덱스 부하 증가, 쿼리 응답 지연 등 성능 저하가 발생할 수 있었습니다.

이런 잠재적 리스크를 줄이기 위해 순수 RDBMS, 서버 세션, Redis 세 가지 옵션을 비교·분석했고, Redis에 15분 TTL을 적용한 하이브리드 방식을 선택했습니다.

결제 완료 시 MySQL에 영구 저장함으로써 빠른 응답 속도와 데이터 무결성을 동시에 확보했습니다.

[Wiki로 자세히 보기](https://github.com/today-is-first/oneul/wiki/%EC%95%84%EB%A6%84%EC%9D%98-%EA%B3%A0%EB%AF%BC%EA%B3%BC-%ED%95%B4%EA%B2%B0)

<br/>

### 🛡️ **보상 트랜잭션 설계로 무결성 보장**

결제 플로우는 외부 Toss 결제 서비스와 내부 MySQL 저장이라는 두 개의 분리된 트랜잭션으로 구성되기 때문에, 외부 결제는 정상 커밋됐지만 내부 DB 트랜잭션이 롤백되면 원자성이 깨지고 데이터 불일치가 발생할 수 있었습니다.

이를 해결하기 위해 Saga 패턴 기반의 보상 트랜잭션(compensation transaction) 구조를 도입했습니다. 내부 DB 저장이 실패하면 곧바로 Toss Cancel API를 호출해 외부 결제를 취소하며, 요청 ID를 활용해 멱등성을 보장합니다.

재시도 상태와 회차는 Redis에 기록하고 최대 3회까지 자동 재시도하며, 이후에는 DLQ(Dead Letter Queue)로 이관해 운영자가 수동으로 환불하도록 설계해 안정성과 운영 효율을 모두 확보했습니다.

[Wiki로 자세히 보기](https://github.com/today-is-first/oneul/wiki/%EC%84%A4%EB%AF%BC%EC%9D%98-%EA%B3%A0%EB%AF%BC%EA%B3%BC-%ED%95%B4%EA%B2%B0)

<br />

<br/>
<br/>

## 🎯 주요 기능 소개

### 💳 결제 및 환급 시스템  
> **"목표 달성, 그 자체가 리워드!"**

**유저는 Toss 결제 API를 통해 챌린지 참가비를 결제**하고,  
**목표를 달성하면 서버가 자동으로 환급 처리를 수행**합니다.  
결제 상태는 **Redis에 임시 저장**, **MySQL에 영구 반영**됩니다.

<img src="https://github.com/user-attachments/assets/94e09687-cfa2-4914-9742-0b6d472b9814" width="700" alt="결제 시스템" />

<br/>
<br/>

### 💬 실시간 채팅  
> **"운동 인증 뒤엔, 응원과 피드백이 바로바로!"**

**채팅은 Netty-Socket.io 기반 WebSocket 서버**와 연결되어 있으며,  
메시지는 **방별로 실시간 브로드캐스팅** 됩니다.

<img src="https://github.com/user-attachments/assets/7090104a-25a5-4c2a-8081-a1e445ce6988" width="700" alt="실시간 채팅" />

<br/>
<br/>

### 📸 인증 피드 업로드  
> **"하루 한 장으로 당신의 변화가 시작됩니다."**

**사용자가 이미지와 내용을 업로드**하면,  
**S3로 이미지 업로드**, 메타데이터는 **MySQL에 저장**됩니다.

<img src="https://github.com/user-attachments/assets/b2049261-6ced-4be9-82ef-6c1eee523804" width="700" alt="인증 피드" />

<br/>
<br/>

### 👑 방장 검수 기능  
> **"진짜 인증만, 진짜로 인정받는다!"**

**방장은 각 피드에 대해 승인/반려 상태를 변경**할 수 있으며,  
변경된 상태는 **DB에 저장**, **리워드 조건에 반영**됩니다.

<img src="https://github.com/user-attachments/assets/0a298cd3-cefe-4032-9cdc-21a6ccc7405a" width="700" alt="방장 승인 기능" />

<br/>
<br/>

### 🧾 마이페이지  
> **"내 운동, 내 챌린지, 내 리워드까지 한 눈에!"**

**챌린지, 피드, 결제 내역** 등을 한눈에 볼 수 있습니다.

<img src="https://github.com/user-attachments/assets/51a9d143-a0a7-49c0-ba44-060dda54f7d1" width="700" alt="마이페이지" />

<br/>
<br/>

## ✅ 서비스 구조도

<img src="https://github.com/user-attachments/assets/93069da6-e930-4185-b0d2-d85e5e3ad024" width="700" alt="오늘 서비스 아키텍처" />

<br/>
<br/>

## ⚒️ Tech Stacks

| 분류 | 기술 스택 |
|------|-----------|
| **Frontend** | [![My Skills](https://skillicons.dev/icons?i=react,vite,tailwind,ts)](https://skillicons.dev) |
| **Backend** | [![My Skills](https://skillicons.dev/icons?i=java,spring)](https://skillicons.dev)  |
| **DB / Infra** | [![My Skills](https://skillicons.dev/icons?i=mysql,redis,nginx,aws)](https://skillicons.dev) |
| **배포** | [![My Skills](https://skillicons.dev/icons?i=githubactions,docker)](https://skillicons.dev) |
| **협업** | [![My Skills](https://skillicons.dev/icons?i=figma,git,github,notion)](https://skillicons.dev) |

<br/>
<br/>

## 🤼 팀원 소개

| 이찬 | 강설민 | 유아름 |
|:---:|:---:|:---:|
| <img src="https://github.com/user-attachments/assets/3bc958ec-4303-4559-b20e-465fe1776e17" width="120"> | <img src="https://github.com/user-attachments/assets/ef01bd70-5a43-4264-b496-3b0874d108de" width="120"> | <img src="https://github.com/user-attachments/assets/259b607f-5a92-4d14-bdc6-91c98ed50149" width="120"> |
| **Frontend / Backend** | **Backend** | **Frontend / Backend** |
| [@today-is-first](https://github.com/today-is-first) | [@seolminkkang](https://github.com/seolminkkang) | [@aoooec](https://github.com/aoooec) |
