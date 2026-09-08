-- ===================================================
-- 0. ?Ёэлкл╓л╕лзлпл╚к╬ф╠юяк╩Ї°╤в√∙ (ыюЁэ?╠їтўк╦▐√Ё╢)
-- ===================================================
BEGIN EXECUTE IMMEDIATE 'DROP TABLE REVIEW_COMMENTS CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE ANIME_REVIEWS CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE BOARD CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP TABLE USERS CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_REVIEW_BNO'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_BOARD_BNO'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_COMMENT_CNO'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

-- ===================================================
-- 1. ?ъмл╞?л╓лыэ┬рў (USERS)
-- б╪ AuthServletк╟к╬лллщлр┘г?Ё╬ємь╢(USER_PW / PASSWORD)к╦??
-- ===================================================
CREATE TABLE USERS (
    USER_ID   VARCHAR2(50) PRIMARY KEY,     -- лц?л╢?ID (ч╚?эо4~20┘■эо)
    USER_PW   VARCHAR2(100) NOT NULL,       -- л╤л╣ля?л╔ (USER_PW?Ё╬щ─)
    PASSWORD  VARCHAR2(100),                -- л╤л╣ля?л╔ (PASSWORD?Ё╬щ─√╗№╡лллщлр)
    USER_NAME VARCHAR2(50) NOT NULL         -- кк┘гюё / л╦л├лпл═?лр
);

-- ===================================================
-- 2. лвл╦лсльл╙лх??у╞ў∙л╞?л╓лыэ┬рў (ANIME_REVIEWS)
-- б╪ BoardServlet.javaк╬лплилък╚л╞?л╓лы┘гкЄш╟юяьщЎ╚
-- ===================================================
CREATE TABLE ANIME_REVIEWS (
    BNO          NUMBER PRIMARY KEY,                                 -- льл╙лх?█у?
    ANIME_TITLE  VARCHAR2(200) NOT NULL,                             -- лвл╦лсэ┬∙б┘г
    TITLE        VARCHAR2(200) NOT NULL,                             -- льл╙лх?л┐лдл╚лы
    CONTENT      CLOB NOT NULL,                                      -- льл╙лх?▄т┘■
    RATING       NUMBER(1) DEFAULT 5 CHECK (RATING BETWEEN 1 AND 5), -- °─? (1~5)
    IMAGE_FILE   VARCHAR2(255),                                      -- лвл├л╫лэ?л╔?▀└л╒лблдлы┘г
    HIT_COUNT    NUMBER DEFAULT 0,                                   -- ???
    WRITER       VARCHAR2(50) NOT NULL,                              -- э┬рўэ║ID
    REG_DATE     DATE DEFAULT SYSDATE,                               -- ╘Ї?ьэу┴
    CONSTRAINT FK_REVIEW_WRITER FOREIGN KEY (WRITER) 
        REFERENCES USERS(USER_ID) ON DELETE CASCADE
);

-- ?Ёэк╬BOARDл╞?л╓лы?Ё╬к╚к╬√╗№╡рїкЄ▄┴к─к┐кск╬VIEW
CREATE OR REPLACE VIEW BOARD AS SELECT * FROM ANIME_REVIEWS;

-- ===================================================
-- 3. льл╙лх?л│лслєл╚л╞?л╓лыэ┬рў (REVIEW_COMMENTS)
-- ===================================================
CREATE TABLE REVIEW_COMMENTS (
    CNO       NUMBER PRIMARY KEY,                                    -- л│лслєл╚█у?
    BNO       NUMBER NOT NULL,                                       -- ?▀┌льл╙лх?█у?
    WRITER    VARCHAR2(50) NOT NULL,                                 -- л│лслєл╚э┬рўэ║ID
    CONTENT   VARCHAR2(1000) NOT NULL,                               -- л│лслєл╚?щ╗
    REG_DATE  DATE DEFAULT SYSDATE,                                  -- ╘Ї?ьэу┴
    CONSTRAINT FK_COMMENT_BNO FOREIGN KEY (BNO) 
        REFERENCES ANIME_REVIEWS(BNO) ON DELETE CASCADE,
    CONSTRAINT FK_COMMENT_WRITER FOREIGN KEY (WRITER) 
        REFERENCES USERS(USER_ID) ON DELETE CASCADE
);

-- ===================================================
-- 4. э╗╘╤єї█ул╖?л▒лєл╣э┬рў
-- б╪ BoardServlet.javaк╟▐┼щ─к╖к╞кдкы SEQ_REVIEW_BNO кЄя╥ы∙
-- ===================================================
CREATE SEQUENCE SEQ_REVIEW_BNO START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_BOARD_BNO START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_COMMENT_CNO START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ===================================================
-- 5. л╤л╒лй?л▐лєл╣·╛▀╛к╬к┐кск╬лдлєл╟л├лпл╣э┬рў
-- ===================================================
CREATE INDEX IDX_REVIEW_REGDATE ON ANIME_REVIEWS(REG_DATE DESC);
CREATE INDEX IDX_REVIEW_WRITER ON ANIME_REVIEWS(WRITER);
CREATE INDEX IDX_COMMENT_BNO ON REVIEW_COMMENTS(BNO);

-- ===================================================
-- 6. Ї°╤вл╟?л┐ўсь¤ (лц?л╢??льл╙лх??л│лслєл╚)
-- ===================================================

-- [1] л╞л╣л╚лц?л╢?╘Ї? (login.html лплдл├лплэл░лдлє╓з╘╤)
INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) 
VALUES ('admin', '1234', '1234', '╬╖╫тэ║');

INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) 
VALUES ('test1', '1234', '1234', 'льл╙лх?л▐л╣л┐?');

INSERT INTO USERS (USER_ID, USER_PW, PASSWORD, USER_NAME) 
VALUES ('kenji', '1234', '1234', 'лвл╦лсл╒лблє╦эьг');

-- [2] лвл╦лсльл╙лх?л╡лєл╫лыл╟?л┐╘Ї?
INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) 
VALUES (
    SEQ_REVIEW_BNO.NEXTVAL,
    '▄╥к╬л╥?лэ?лвллл╟л▀лв',
    '╨╝э▐ў·гжї╠уцл╖?л║лєк╬??э┬?к╚ц╤їєкм?╙юю▄гб',
    'лнлулщлпл┐?ьщь╤к╥к╚кък╬рўэ■к╚ус╥╖кмя╦╥╗к╦┘┌клкьк╞кккъбвлплщлдл▐л├лпл╣к╬л╨л╚лылвлпл╖лчлєк╧Ёш╤┐км╪бк─к█к╔к╬уъэ┬?к╟к╖к┐бгых?к╚к╬л╖лєлплэсуктш╟█¤к╟┘■╧гк╩к╖к╬┘гэ┬к╟к╣гб',
    5,
    NULL,
    42,
    'test1',
    SYSDATE - 2
);

INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) 
VALUES (
    SEQ_REVIEW_BNO.NEXTVAL,
    '╤ж╘╤?▐═лмлєл└лр рь╬├к╬л╧л╡лжлзлд',
    'у╝╩╢Єв?к╬лълвлъл╞лгк╚хи╩р??к╬ёь¤зк╩ц╤їє',
    '??к╬лслллвлпл╖лчлєкЄї▒кик┐ых·┬рт═кк╚╤╠┌▐╩як╬квкылллслщля?лпкмс╚Їчкщк╖кдбг╙▐ь╤к╬╩№▀█к╦╥▒кикжкыёь¤зк╩л╣л╚?лъ?юў╦╥к╟бв?°║к╪к╬╤в╙ткм▐к▀╚к╦═╘к▐кыьщэ┬к╟к╣бг',
    5,
    NULL,
    35,
    'kenji',
    SYSDATE - 1
);

INSERT INTO ANIME_REVIEWS (BNO, ANIME_TITLE, TITLE, CONTENT, RATING, IMAGE_FILE, HIT_COUNT, WRITER, REG_DATE) 
VALUES (
    SEQ_REVIEW_BNO.NEXTVAL,
    'к┴кдклкя',
    '╩жфёк╡к╬ёщк╦?крл╖лх?лык╡к╚?ўхк╩сж═г?к╬╪х╒Ї',
    '1№еквк┐кък╬у┴╩рк╧╙нкдк╟к╣кмбвл╞лєл▌к╬╒▐кдл│л▀лллык╩юў╦╥к╚?ўхк╬╤╠эх╩якм█■к╦к╩кък▐к╣бг∙ккьк┐ьэ▀╚к╬ыик╖к╚к╖к╞?Ё╚к╬?к╖к▀к╦ї╠юък╟к╣бг',
    4,
    NULL,
    18,
    'admin',
    SYSDATE
);

-- [3] л╡лєл╫лыл│лслєл╚╘Ї?
INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE)
VALUES (SEQ_COMMENT_CNO.NEXTVAL, 1, 'kenji', 'л╥лэлвллк╬╨╝э▐ў·ц╤їєбв▄т?к╦ї╠═╘к╟к╖к┐кшк═гбш╟юяк╦╘╥╩як╟к╣бг', SYSDATE - 1);

INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE)
VALUES (SEQ_COMMENT_CNO.NEXTVAL, 1, 'admin', 'э┬?л┴?лрк╬▄т?кЄ╩як╕кылплклъл╞лгк╟к╖к┐бг', SYSDATE - 1);

INSERT INTO REVIEW_COMMENTS (CNO, BNO, WRITER, CONTENT, REG_DATE)
VALUES (SEQ_COMMENT_CNO.NEXTVAL, 2, 'test1', 'хи╩р??к╬л│л├лпл╘л├л╚у╩я├к╬┘┌?к╧∙╝╙°╠╕к╞ктЁш╤┐км╪бк┴к▐к╣бг', SYSDATE);

-- ?╠┌№мя╥
COMMIT;