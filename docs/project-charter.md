# CS 451 Software Engineering Capstone
## Project Charter

---

## Project Information

| Document Field | Team Entry |
|---|---|
| **Project Title** | Trip Budget Planner |
| **Team Name** | Hello World |
| **Team Members** | Hafsa Ahmed, Oh May Ishar, Mahnoor Shahid, and Hajar Wilkes |
| **Project Manager / Team Lead** | Mahnoor Shahid |
| **Project Sponsor** | Professor & Commerce Bank |
| **Customer / Client** | Commerce Bank |
| **Instructor** | Shah, Syed Jawad Hussain |
| **Start and Target End Dates** | August 2026 – December 2026 |
| **Version Status and Date** | Draft, September 22, 2026 |

### Revision History

| Version | Date | Author | Change and Approval |
|---|---|---|---|
| 1.0 | 09/25/2026 | Hafsa, Oh May, Mahnoor, & Hajar | Initial draft |

---

# 1. Project Purpose and Authorization

## 1.1 Problem or Opportunity

Planning a trip can be difficult because travelers have to manage their trip details, estimated expenses, and savings in different places. It can be hard for users to understand how much a trip will actually cost and how much they need to save before their travel date.

This can lead to:

- Poor budgeting
- Unexpected expenses
- Difficulty reaching a travel savings goal

There is an opportunity to make trip planning and financial planning easier to manage together.

## 1.2 Project Justification

| Decision Test | Team Explanation | Evidence or Source |
|---|---|---|
| **Needed** | Travelers need an easier way to plan a trip while also understanding the total cost and how much they need to save to afford it. | Commerce Bank sponsor |
| **Feasible** | Our team has the programming, database, web development, and software engineering skills needed to build a functional prototype within the semester by developing it in smaller features throughout the semester. | The team has technical experience, available development tools, and a semester timeline. |
| **Optimum** | This project combines trip planning and budgeting into one application. | Comparison with using separate travel planning and budgeting tools. |

## 1.3 Product Vision

> For travelers who need an easier way to plan and afford a trip, the Trip Budget Planner is a financial travel planning application that helps users organize their trip expenses, create a savings plan, and track their progress toward their travel goal.

## 1.4 Authority to Proceed

| Authorization Item | Entry |
|---|---|
| **Decision** | Approved to proceed |
| **Conditions** | Continue refining project requirements and scope based on professor feedback. |
| **Authority Granted** | The team may proceed with requirements development, system design, prototyping, and Sprint 1 planning. |

---

# 2. Stakeholders and Team Authority

## 2.1 Stakeholder Register

| Stakeholder / Group | Interest, Needs, or Expectations | Decision or Contribution | Contact or Review Cadence |
|---|---|---|---|
| **Commerce Bank Tech Mentors** | A working full-stack application that satisfies the required financial authentication, database, frontend, and testing requirements | Provide technical guidance, feedback, and clarification throughout the project | Anar Agayev, Julio Gracida, Clayton Sloniker. Project introduction/requirements meeting, mid-semester check-in, and final presentation; email available as needed |
| **Commerce Bank Project Coordinator** | Successful coordination of the capstone project and communication between Commerce Bank and all student teams | Coordinates project communication and provides project-related guidance | Becca Fantasma. Project meetings and communication as needed |
| **Target Users** | An application that is usable, readable, properly aligned, and easy to navigate | Provide feedback that reflects the application's usability | During development and testing |
| **Instructor** | Completion of the capstone project as well as all required components in a timely manner based on course expectations | Provides project guidance and evaluates project work | Throughout the semester |
| **Student Team** | Completes requirements and successful completion of a functional full-stack application | Designs, develops, tests, documents, and presents a complete fully-functional application | Weekly team meetings and ongoing daily collaboration |

## 2.2 Team Roles and Responsibilities

| Team Member | Primary Role | Charter Responsibility | Decision Authority / Backup |
|---|---|---|---|
| **Mahnoor Shahid** | Project Manager / Backend Developer | Coordinate project planning, deadlines, team responsibilities, and project progress while contributing to backend development. | Project coordination and backend decisions; backup to be determined |
| **OhMay Ishar** | Frontend / UI/UX Developer | Develop the frontend, application pages, and user interface while helping ensure the application is usable and consistent. | Frontend and UI/UX decisions; backup to be determined |
| **Hafsa Ahmed** | Backend Developer / Requirements Analyst | Contribute to backend development while defining and documenting requirements, use cases, and stakeholder needs. | Backend and requirements decisions; backup to be determined |
| **Hajar Wilkes** | Backend / Database Developer / Quality Analyst / QAr | Contribute to backend and database development while developing and carrying out testing to verify that requirements and functionality are met. | Database/backend and QA decisions; backup to be determined |

> All team members are responsible for contributing to project documentation, including requirements, design, testing, technical documentation, and final project materials.

## 2.3 Decision and Escalation Rules

The team will make routine project decisions through discussion and collaboration, with input from all team members.

When a decision relates to a specific area of responsibility, the team member responsible for that area will provide recommendations and help guide the decision.

If the team cannot reach an agreement, the Project Manager will facilitate discussion and help the team reach a resolution. It will primarily be based on what the majority of the team agrees on.

Issues involving major changes to project scope, requirements, deadlines, or other decisions that cannot be resolved by the team will be brought to the instructor for a resolution.

---

# 3. Goals, Objectives, and High-Level Features

## 3.1 Project Goals

| Goal ID | Goal Statement | Why It Matters |
|---|---|---|
| **G-01** | Create an application that helps users plan trips while managing their budget and savings goals in one place. | Users can better understand the total cost of their trip and prepare financially before traveling. |
| **G-02** | Provide users with tools to organize trip details, including destinations, expenses, accommodations, activities, and shared trip planning. | Users can keep their travel information organized instead of using multiple separate tools. |
| **G-03** | Use AI features to provide personalized trip recommendations based on user preferences and budget. | Users can receive suggestions that match their travel priorities and make planning easier. |

## 3.2 Measurable Objectives

| Objective ID | Specific Measurable Objective | Target Date | How the Team Will Verify It |
|---|---|---|---|
| **OBJ-01** | Sprint 0 – Documentation, GitHub & Jira setup | 09/25/2026 | Proofread documents & Canvas submission |
| **OBJ-02** | Sprint 1 – UI/UX design, workflow | 10/02/2026 | Team review |

## 3.3 High-Level Features

| Feature ID | Client-Valued Feature | Primary User / Stakeholder | Priority |
|---|---|---|---|
| **F-01** | User Registration & Login | Traveler / User | **Must** |
| **F-02** | Trip Creation & Management | Traveler / User | **Must** |
| **F-03** | Trip Budget Planning | Traveler / User | **Must** |
| **F-04** | Savings Goal & Savings Plan Tracking | Traveler / User | **Must** |
| **F-05** | Actual Trip Expense Tracking | Traveler / User | **Must** |
| **F-06** | AI Trip Planning, Recommendations & Estimates | Traveler / User | **Should** |
| **F-07** | Shared Trips & Trip Members | Traveler / User | **Should** |
| **F-08** | Bank Account Connection | Traveler / User | **Should** |
| **F-09** | Trip Deletion / Cancellation | Traveler / User | **Should** |
| **F-10** | Currency Conversion & Exchange Rate Support | Traveler / User | **Could** |

---

# 4. Scope, Priorities, and Deliverables

## 4.1 Scope Boundaries

### In Scope

- User registration and login
- Trip creation and management
- Trip budget planning with expense categories
- Saving goals
- Financial workflow:
  - Create a trip budget
  - Estimate trip expenses
  - Set a savings goal
  - Track savings progress
  - Track actual trip expenses
- Required pages:
  - Login / Registration
  - Dashboard
  - Trip Creation / Management
  - Budget Planning
  - Savings Tracker
  - Expense Tracker

### Out of Scope

- Real money transfer or payment processing
- Use of real financial or card information
- Production-level banking services
- Features that cannot reasonably be completed within the semester
- Real banking connections
- Real money movement
- Real card data unless the sponsor explicitly changes the brief

## 4.2 Project Priorities

| Priority Area | Rank | Team Agreement |
|---|---:|---|
| **Scope and Product Value** | 2 | Protect the core trip budgeting and savings features. Lower-priority features may be reduced or removed if needed. |
| **Schedule and Course Deadlines** | 1 | Course deadlines must be met. The team will reduce or adjust scope rather than miss a required deadline. |
| **Quality and Technical Standards** | 3 | The application must be functional, tested, and meet the project requirements before delivery. |

## 4.3 Major Deliverables and Milestones

| ID | Milestone / Deliverable | Tangible Evidence | Target Date / Sprint | Owner | Reviewer |
|---|---|---|---|---|---|
| **M-01** | Project charter approved | Approved charter | Sprint 0 | All Team Members | Instructor |
| **M-02** | Requirements baseline | Use cases, FRs, NFRs, and traceability | September 2026 | All Team Members | Instructor |
| **M-03** | Architecture candidate reviewed | Architecture document and decision record | October 2026 | All Team Members | Instructor |
| **M-04** | Midsemester increment | Working product increment and feedback notes | October 2026 | All Team Members | Sponsor and Instructor |
| **M-05** | Feature complete | Testable release candidate | November 2026 | All Team Members | Instructor |
| **M-06** | Final delivery | Application, tests, documents, and presentation | December 2026 | All Team Members | Sponsor and Instructor |

---

# 5. Resources, Constraints, and Assumptions

## 5.1 Preliminary Resource and Budget Estimate

| Resource | Estimated Quantity / Effort | Estimated Cost | Source / Availability | Owner |
|---|---|---|---|---|
| **Student Effort** | 5–10 hours per week | $0 course labor | Team availability | All Team Members |
| **Hosting or Cloud Services** | Free-tier hosting and database services | $0 course labor | Online cloud services | Undecided |
| **Development Tools** | GitHub, Jira, Figma, Visual Studio Code | $0 course labor | Free/student development tools | All Team Members |
| **Equipment or Test Devices** | Personal laptops/computers | $0 course labor | Team-owned equipment | All Team Members |
| **Other** | External APIs / AI services, if needed | $0 course labor | Available API free tiers | All Team Members |

## 5.2 Constraints

| Constraint ID | Limit Placed on Product / Project | Source | Effect on Team |
|---|---|---|---|
| **C-01** | Complete the project within the course schedule | Course calendar | The team must adjust scope before missing a required deadline. |
| **C-02** | Use only fabricated financial and card data | Sponsor brief | The product cannot connect to real accounts or process real money. |
| **C-03** | Use only technologies and services available to the team | Team resources | Team may need to adjust features if a required tool or service is unavailable. |
| **C-04** | Limited development time and team availability | Semester schedule / team availability | Team must prioritize required features before optional features. |

## 5.3 Assumptions and Dependencies

| ID | Type | Statement | How the Team Will Validate / Respond | Owner | Review Date |
|---|---|---|---|---|---|
| **A-01** | Assumption | Team members will have access to the required development tools and reliable internet. | Confirm access to all required tools at the beginning of development. | All Team Members | Sprint 1 |
| **D-01** | Dependency | Project depends on GitHub, Jira, Figma, and VS Code for development collaboration. | Use available alternatives if services become temporarily unavailable. | All Team Members | Throughout semester |
| **A-02** | Assumption | User will provide the trip and budget information needed to create their financial plan. | Verify through requirements and application testing. | All Team Members | Sprint 1 |

---

# 6. Success Criteria, Risks, and Agile Working Agreement

## 6.1 Project Success Criteria

| Criterion ID | What Success Means | Measure / Threshold | Evidence | Reviewer |
|---|---|---|---|---|
| **SC-01** | A user can complete the main trip budgeting workflow. | User can register/login, create a trip, create a budget, and create or view a savings goal without a blocking error. | Final demonstration and functional testing | Instructor and Sponsor |
| **SC-02** | Core application features function correctly using test or fabricated data. | Required core features operate as described in the approved requirements and use cases. | Test results and application demonstration | Instructor |
| **SC-03** | The team delivers the required project materials by the course deadline. | Final application, tests, documentation, and presentation are submitted by the required deadline. | Final submission and presentation | Instructor and Sponsor |

## 6.2 Initial Risks and Obstacles

| Risk ID | Potential Problem | Likelihood | Impact | First Response | Owner |
|---|---|---|---|---|---|
| **R-01** | The team may not have enough development time to complete every planned feature during the semester. | Medium | High | Complete Must features first and reduce or postpone lower-priority features if necessary. | All Team Members |
| **R-02** | External APIs or AI services may be unavailable, difficult to integrate, or exceed free-tier limitations. | Medium | Medium | Treat API-dependent features as lower priority and use simplified or sample data when appropriate. | All Team Members |
| **R-03** | Technical issues may occur while integrating the frontend, backend, and database. | Medium | Medium | Integrate components incrementally and test major workflows throughout development rather than waiting until the end. | All Team Members |
| **R-04** | Differences in team availability may delay assigned tasks. | Medium | Medium | Track tasks and deadlines regularly and reassign or reduce lower-priority work when necessary. | Project Manager |

## 6.3 Agile Working Agreement

| Working Practice | Team Agreement |
|---|---|
| **Sprint Length and Ceremonies** | Estimated sprint length: ~2 weeks. Planning: every Friday. Standup: every Monday, Wednesday, Friday. |
| **Backlog Ownership** | Mahnoor Shahid |
| **Definition of Done** | Team review, passed tests, and finished documentation. |
| **Communication and Response Time** | Communication through text, average response time within a couple of hours, and be present at least every Friday. |
| **Scope Change** | Mutual team agreement |
| **Conflict or Missed Commitment** | Project manager intervenes and helps reach a resolution. |

---

# 7. Approval and Submission Check

Approval confirms that the reviewers understand the project purpose, boundaries, expected outcomes, and initial authority. It does not freeze the entire backlog. Record approved charter changes in the revision history.

## 7.1 Review Decisions

| Reviewer | Role | Decision | Conditions / Comments | Date |
|---|---|---|---|---|
| `[Name]` | Project Sponsor | `[Approve / Revise]` | `[Conditions or comments]` | `[Date]` |
| **Shah, Syed Jawad Hussain** | Instructor | `[Approve / Revise]` | `[Conditions or comments]` | `[Date]` |
| `[Name]` | Student Project Lead | `[Accept team commitment]` | `[Comments]` | `[Date]` |

## 7.2 Signatures

| Name and Role | Signature | Date |
|---|---|---|
| `[Project Sponsor]` | | |
| `[Instructor]` | | |
| `[Student Project Lead]` | | |
| `[Additional Approver if Required]` | | |

## 7.3 Final Submission Checklist

- [ ] The problem or opportunity is clear and does not begin with a predetermined solution.
- [ ] The charter explains why the project is needed, feasible, and a responsible use of resources.
- [ ] Stakeholders, team responsibilities, and decision authority are clear.
- [ ] Goals, measurable objectives, and high-level features agree with one another.
- [ ] In-scope and out-of-scope boundaries can guide backlog decisions.
- [ ] Milestones name tangible deliverables, target dates, owners, and reviewers.
- [ ] Constraints, assumptions, dependencies, success criteria, and initial risks are recorded.
- [ ] The agile working agreement explains how the team will plan, review, test, communicate, and handle changes.
- [ ] Every bracketed prompt has been replaced or removed before submission.
- [ ] The version, date, author list, and approval decisions are current.
